package com.hitomic.hitomicdraw.storage;

import com.hitomic.hitomicdraw.HitomicDrawPlugin;
import com.hitomic.hitomicdraw.model.Drawing;
import com.hitomic.hitomicdraw.model.DrawingState;
import com.hitomic.hitomicdraw.model.Palette;
import com.hitomic.hitomicdraw.model.Submission;
import com.hitomic.hitomicdraw.model.SubmissionStatus;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Guarda desenhos (drawings.yml) e participacoes (submissions.yml).
 *
 * <p>Todo o estado vive em memoria e so e acessado pela thread principal. A gravacao
 * em disco e feita em uma thread separada: a thread principal apenas gera o texto
 * YAML e entrega para o executor, que escreve de forma atomica (arquivo temporario
 * + rename), entao um crash no meio do salvamento nunca corrompe o arquivo.</p>
 */
public final class DataStore {

    private final HitomicDrawPlugin plugin;
    private final File drawingsFile;
    private final File submissionsFile;

    private final Map<Integer, Drawing> drawings = new LinkedHashMap<>();
    private final Map<Integer, Submission> submissions = new LinkedHashMap<>();
    private final Map<Integer, Integer> drawingByMap = new HashMap<>();
    private final Map<Integer, Integer> submissionByDrawing = new HashMap<>();
    private int nextDrawingId = 1;
    private int nextSubmissionId = 1;

    private final AtomicBoolean drawingsDirty = new AtomicBoolean();
    private final AtomicBoolean submissionsDirty = new AtomicBoolean();
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "HitomicDraw-IO");
        thread.setDaemon(true);
        return thread;
    });
    private BukkitTask autosave;

    public DataStore(HitomicDrawPlugin plugin) {
        this.plugin = plugin;
        this.drawingsFile = new File(plugin.getDataFolder(), "drawings.yml");
        this.submissionsFile = new File(plugin.getDataFolder(), "submissions.yml");
    }

    // ------------------------------------------------------------------ carga

    /** Leitura inicial, feita uma unica vez no onEnable. */
    public void load() {
        loadDrawings();
        loadSubmissions();
        plugin.getLogger().info("Carregados " + drawings.size() + " desenhos e "
                + submissions.size() + " participacoes.");
    }

    private YamlConfiguration read(File file) {
        YamlConfiguration cfg = new YamlConfiguration();
        if (!file.exists()) {
            return cfg;
        }
        try {
            cfg.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            File backup = new File(file.getParentFile(), file.getName() + ".corrompido-" + System.currentTimeMillis());
            plugin.getLogger().log(Level.SEVERE, "Nao foi possivel ler " + file.getName()
                    + ". O arquivo sera preservado como " + backup.getName() + " e o plugin comecara vazio.", ex);
            if (!file.renameTo(backup)) {
                plugin.getLogger().severe("Falha ao renomear " + file.getName() + " para backup.");
            }
            return new YamlConfiguration();
        }
        return cfg;
    }

    private void loadDrawings() {
        YamlConfiguration cfg = read(drawingsFile);
        nextDrawingId = Math.max(1, cfg.getInt("next-id", 1));
        ConfigurationSection section = cfg.getConfigurationSection("drawings");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                UUID owner = UUID.fromString(s.getString("owner", ""));
                Drawing drawing = new Drawing(id, owner, s.getString("owner-name", "?"), s.getLong("created-at"));
                drawing.setMapId(s.getInt("map-id", -1));
                drawing.setState(DrawingState.valueOf(s.getString("state", "DRAFT")));
                drawing.setFinishedAt(s.getLong("finished-at"));
                drawing.setSubmittedAt(s.getLong("submitted-at"));
                List<String> pixels = s.getStringList("pixels");
                for (int i = 0; i < Drawing.SIZE; i++) {
                    drawing.setPixel(i, i < pixels.size() ? Palette.fromHex(pixels.get(i)) : Palette.BACKGROUND);
                }
                register(drawing);
                nextDrawingId = Math.max(nextDrawingId, id + 1);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Desenho invalido em drawings.yml (chave " + key + "); ignorado.", ex);
            }
        }
    }

    private void loadSubmissions() {
        YamlConfiguration cfg = read(submissionsFile);
        nextSubmissionId = Math.max(1, cfg.getInt("next-id", 1));
        ConfigurationSection section = cfg.getConfigurationSection("submissions");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                Submission submission = new Submission(
                        id,
                        s.getInt("drawing-id"),
                        UUID.fromString(s.getString("player", "")),
                        s.getString("player-name", "?"),
                        s.getLong("submitted-at"));
                submission.setStatus(SubmissionStatus.valueOf(s.getString("status", "PENDING")));
                String reviewer = s.getString("reviewer");
                if (reviewer != null && !reviewer.isBlank()) {
                    submission.setReviewer(UUID.fromString(reviewer));
                }
                submission.setReviewerName(s.getString("reviewer-name"));
                submission.setReviewedAt(s.getLong("reviewed-at"));
                submission.setNote(s.getString("note"));
                submission.setNotified(s.getBoolean("notified", false));
                submissions.put(id, submission);
                submissionByDrawing.put(submission.getDrawingId(), id);
                nextSubmissionId = Math.max(nextSubmissionId, id + 1);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Participacao invalida em submissions.yml (chave " + key + "); ignorada.", ex);
            }
        }
    }

    // --------------------------------------------------------------- consultas

    private void register(Drawing drawing) {
        drawings.put(drawing.getId(), drawing);
        if (drawing.getMapId() >= 0) {
            drawingByMap.put(drawing.getMapId(), drawing.getId());
        }
    }

    public Drawing getDrawing(int id) {
        return drawings.get(id);
    }

    public Drawing getDrawingByMap(int mapId) {
        Integer id = drawingByMap.get(mapId);
        return id == null ? null : drawings.get(id);
    }

    public Collection<Drawing> getDrawings() {
        return Collections.unmodifiableCollection(drawings.values());
    }

    public List<Drawing> getDrawingsOf(UUID owner) {
        List<Drawing> result = new ArrayList<>();
        for (Drawing d : drawings.values()) {
            if (d.getOwner().equals(owner)) {
                result.add(d);
            }
        }
        return result;
    }

    /** Desenhos do jogador ainda nao enviados (rascunho ou finalizado). */
    public List<Drawing> getActiveDrawings(UUID owner) {
        List<Drawing> result = new ArrayList<>();
        for (Drawing d : drawings.values()) {
            if (d.getOwner().equals(owner) && d.getState() != DrawingState.SUBMITTED) {
                result.add(d);
            }
        }
        return result;
    }

    public Submission getSubmission(int id) {
        return submissions.get(id);
    }

    public Submission getSubmissionByDrawing(int drawingId) {
        Integer id = submissionByDrawing.get(drawingId);
        return id == null ? null : submissions.get(id);
    }

    public List<Submission> getSubmissions() {
        return new ArrayList<>(submissions.values());
    }

    public List<Submission> getUnnotified(UUID player) {
        List<Submission> result = new ArrayList<>();
        for (Submission s : submissions.values()) {
            if (s.getPlayer().equals(player) && s.getStatus() != SubmissionStatus.PENDING && !s.isNotified()) {
                result.add(s);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- mudancas

    public Drawing createDrawing(UUID owner, String ownerName) {
        Drawing drawing = new Drawing(nextDrawingId++, owner, ownerName, System.currentTimeMillis());
        register(drawing);
        markDrawingsDirty();
        return drawing;
    }

    public void removeDrawing(Drawing drawing) {
        drawings.remove(drawing.getId());
        drawingByMap.remove(drawing.getMapId());
        markDrawingsDirty();
    }

    public void bindMap(Drawing drawing, int mapId) {
        if (drawing.getMapId() >= 0) {
            drawingByMap.remove(drawing.getMapId());
        }
        drawing.setMapId(mapId);
        drawingByMap.put(mapId, drawing.getId());
        markDrawingsDirty();
    }

    /** @return a nova participacao, ou null se este desenho ja foi enviado antes. */
    public Submission createSubmission(Drawing drawing, String playerName) {
        if (submissionByDrawing.containsKey(drawing.getId())) {
            return null;
        }
        Submission submission = new Submission(nextSubmissionId++, drawing.getId(), drawing.getOwner(),
                playerName, System.currentTimeMillis());
        submissions.put(submission.getId(), submission);
        submissionByDrawing.put(drawing.getId(), submission.getId());
        markSubmissionsDirty();
        return submission;
    }

    public void markDrawingsDirty() {
        drawingsDirty.set(true);
    }

    public void markSubmissionsDirty() {
        submissionsDirty.set(true);
    }

    // ----------------------------------------------------------------- salvar

    public void startAutosave() {
        long ticks = Math.max(5L, plugin.getConfig().getLong("settings.autosave-seconds", 30L)) * 20L;
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::saveNow, ticks, ticks);
    }

    /** Salva (de forma assincrona) tudo o que estiver marcado como alterado. */
    public void saveNow() {
        try {
            if (drawingsDirty.getAndSet(false)) {
                writeAsync(drawingsFile, serializeDrawings());
            }
            if (submissionsDirty.getAndSet(false)) {
                writeAsync(submissionsFile, serializeSubmissions());
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao preparar dados para salvar.", ex);
        }
    }

    /** Chamado no onDisable: espera gravacoes pendentes e faz a gravacao final. */
    public void shutdown() {
        if (autosave != null) {
            autosave.cancel();
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Gravacoes pendentes nao terminaram a tempo.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        try {
            if (drawingsDirty.getAndSet(false)) {
                writeAtomic(drawingsFile.toPath(), serializeDrawings());
            }
            if (submissionsDirty.getAndSet(false)) {
                writeAtomic(submissionsFile.toPath(), serializeSubmissions());
            }
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Erro na gravacao final dos dados.", ex);
        }
    }

    private String serializeDrawings() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("next-id", nextDrawingId);
        for (Drawing d : drawings.values()) {
            String p = "drawings." + d.getId() + ".";
            cfg.set(p + "owner", d.getOwner().toString());
            cfg.set(p + "owner-name", d.getOwnerName());
            cfg.set(p + "map-id", d.getMapId());
            cfg.set(p + "state", d.getState().name());
            cfg.set(p + "created-at", d.getCreatedAt());
            cfg.set(p + "finished-at", d.getFinishedAt());
            cfg.set(p + "submitted-at", d.getSubmittedAt());
            List<String> pixels = new ArrayList<>(Drawing.SIZE);
            for (int i = 0; i < Drawing.SIZE; i++) {
                pixels.add(Palette.get(d.getPixel(i)).hex());
            }
            cfg.set(p + "pixels", pixels);
        }
        return cfg.saveToString();
    }

    private String serializeSubmissions() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("next-id", nextSubmissionId);
        for (Submission s : submissions.values()) {
            String p = "submissions." + s.getId() + ".";
            cfg.set(p + "drawing-id", s.getDrawingId());
            cfg.set(p + "player", s.getPlayer().toString());
            cfg.set(p + "player-name", s.getPlayerName());
            cfg.set(p + "submitted-at", s.getSubmittedAt());
            cfg.set(p + "status", s.getStatus().name());
            cfg.set(p + "reviewer", s.getReviewer() == null ? null : s.getReviewer().toString());
            cfg.set(p + "reviewer-name", s.getReviewerName());
            cfg.set(p + "reviewed-at", s.getReviewedAt());
            cfg.set(p + "note", s.getNote());
            cfg.set(p + "notified", s.isNotified());
        }
        return cfg.saveToString();
    }

    private void writeAsync(File file, String content) {
        if (io.isShutdown()) {
            return;
        }
        try {
            io.execute(() -> {
                try {
                    writeAtomic(file.toPath(), content);
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Erro ao salvar " + file.getName(), ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            plugin.getLogger().log(Level.WARNING, "Executor de gravacao encerrado; " + file.getName() + " nao foi salvo agora.", ex);
        }
    }

    private static void writeAtomic(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
