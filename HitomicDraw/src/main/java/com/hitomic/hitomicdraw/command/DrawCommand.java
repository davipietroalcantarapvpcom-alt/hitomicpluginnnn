package com.hitomic.hitomicdraw.command;

import com.hitomic.hitomicdraw.HitomicDrawPlugin;
import com.hitomic.hitomicdraw.gui.EditorManager;
import com.hitomic.hitomicdraw.gui.ReviewGui;
import com.hitomic.hitomicdraw.model.Drawing;
import com.hitomic.hitomicdraw.model.DrawingState;
import com.hitomic.hitomicdraw.model.Submission;
import com.hitomic.hitomicdraw.model.SubmissionStatus;
import com.hitomic.hitomicdraw.service.DrawingService;
import com.hitomic.hitomicdraw.service.MapService;
import com.hitomic.hitomicdraw.storage.DataStore;
import com.hitomic.hitomicdraw.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** Comando /desenhar e subcomandos. */
public final class DrawCommand implements TabExecutor {

    private static final int PAGE_SIZE = 8;
    private static final List<String> PLAYER_SUBS = List.of("criar", "abrir", "receber", "meus", "ajuda");
    private static final List<String> ADMIN_SUBS = List.of("avaliacoes", "ver", "aprovar", "rejeitar", "recarregar");
    private static final List<String> FILTERS = List.of("pendentes", "aprovadas", "rejeitadas", "todas");

    private final HitomicDrawPlugin plugin;
    private final DataStore store;
    private final MapService maps;
    private final DrawingService service;
    private final EditorManager editor;
    private final ReviewGui review;
    private final Messages messages;

    public DrawCommand(HitomicDrawPlugin plugin, DataStore store, MapService maps, DrawingService service,
                       EditorManager editor, ReviewGui review, Messages messages) {
        this.plugin = plugin;
        this.store = store;
        this.maps = maps;
        this.service = service;
        this.editor = editor;
        this.review = review;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            dispatch(sender, args);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao executar /desenhar", ex);
            messages.send(sender, "error-internal");
        }
        return true;
    }

    private void dispatch(CommandSender sender, String[] args) {
        if (args.length == 0) {
            help(sender);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "criar" -> create(sender);
            case "abrir" -> open(sender, args);
            case "receber" -> receive(sender, args);
            case "meus" -> mine(sender);
            case "avaliacoes", "avaliações" -> list(sender, args);
            case "ver" -> view(sender, args);
            case "aprovar" -> evaluate(sender, args, true);
            case "rejeitar" -> evaluate(sender, args, false);
            case "recarregar" -> reload(sender);
            default -> help(sender);
        }
    }

    // ---------------------------------------------------------------- jogador

    private void help(CommandSender sender) {
        for (String line : messages.list("help-player")) {
            sender.sendMessage(Messages.legacy(line));
        }
        if (sender.hasPermission(HitomicDrawPlugin.PERM_ADMIN)) {
            for (String line : messages.list("help-admin")) {
                sender.sendMessage(Messages.legacy(line));
            }
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "only-players");
            return null;
        }
        if (!player.hasPermission(HitomicDrawPlugin.PERM_USE)) {
            messages.send(player, "no-permission");
            return null;
        }
        return player;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission(HitomicDrawPlugin.PERM_ADMIN)) {
            messages.send(sender, "no-permission");
            return false;
        }
        return true;
    }

    private void create(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        int max = plugin.getConfig().getInt("settings.max-active-drawings", 3);
        if (store.getActiveDrawings(player.getUniqueId()).size() >= max) {
            messages.send(player, "limit-reached", "max", String.valueOf(max));
            return;
        }
        Drawing drawing = service.createDrawing(player);
        ItemStack item = maps.createItem(drawing, false);
        String id = String.valueOf(drawing.getId());
        if (service.giveItem(player, item)) {
            messages.send(player, "created", "id", id);
        } else {
            messages.send(player, "created-full", "id", id);
        }
        if (plugin.getConfig().getBoolean("settings.open-editor-on-create", false)) {
            editor.open(player, drawing);
        }
    }

    /** Descobre qual desenho do jogador o comando se refere (id, mapa na mao ou unico ativo). */
    private Drawing resolveOwn(Player player, String[] args) {
        if (args.length > 1) {
            Integer id = parseId(args[1]);
            Drawing drawing = id == null ? null : store.getDrawing(id);
            if (drawing == null) {
                messages.send(player, "drawing-not-found");
                return null;
            }
            if (!drawing.getOwner().equals(player.getUniqueId())) {
                messages.send(player, "not-owner", "owner", drawing.getOwnerName());
                return null;
            }
            return drawing;
        }
        Integer held = maps.readDrawingId(player.getInventory().getItemInMainHand());
        if (held != null) {
            Drawing drawing = store.getDrawing(held);
            if (drawing != null) {
                if (!drawing.getOwner().equals(player.getUniqueId())) {
                    messages.send(player, "not-owner", "owner", drawing.getOwnerName());
                    return null;
                }
                return drawing;
            }
        }
        List<Drawing> active = store.getActiveDrawings(player.getUniqueId());
        if (active.isEmpty()) {
            messages.send(player, "no-drawings");
            return null;
        }
        if (active.size() == 1) {
            return active.get(0);
        }
        List<String> ids = new ArrayList<>();
        for (Drawing d : active) {
            ids.add("#" + d.getId());
        }
        messages.send(player, "choose-drawing", "ids", String.join(", ", ids));
        return null;
    }

    private void open(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Drawing drawing = resolveOwn(player, args);
        if (drawing == null) {
            return;
        }
        String id = String.valueOf(drawing.getId());
        switch (drawing.getState()) {
            case DRAFT -> editor.open(player, drawing);
            case FINISHED -> messages.send(player, "already-finished", "id", id);
            case SUBMITTED -> messages.send(player, "already-submitted", "id", id);
        }
    }

    private void receive(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Drawing drawing = resolveOwn(player, args);
        if (drawing == null) {
            return;
        }
        String id = String.valueOf(drawing.getId());
        if (drawing.getState() == DrawingState.SUBMITTED) {
            messages.send(player, "already-submitted", "id", id);
            return;
        }
        if (maps.hasInInventory(player, drawing.getId())) {
            messages.send(player, "already-have", "id", id);
            return;
        }
        if (service.giveItem(player, maps.createItem(drawing, false))) {
            messages.send(player, "received", "id", id);
        } else {
            messages.send(player, "inventory-full", "id", id);
        }
    }

    private void mine(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        List<Drawing> own = store.getDrawingsOf(player.getUniqueId());
        if (own.isEmpty()) {
            messages.send(player, "mine-empty");
            return;
        }
        messages.send(player, "mine-header");
        for (Drawing drawing : own) {
            String state = messages.raw("state-" + drawing.getState().name().toLowerCase(Locale.ROOT));
            String result = "";
            Submission submission = store.getSubmissionByDrawing(drawing.getId());
            if (submission != null) {
                result = " &8(participacao #" + submission.getId() + ": " + review.statusText(submission.getStatus()) + "&8)";
            }
            player.sendMessage(Messages.legacy(messages.raw("mine-line",
                    "id", String.valueOf(drawing.getId()), "state", state, "result", result)));
        }
    }

    // ---------------------------------------------------------- administracao

    private void list(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        String filter = "pendentes";
        int page = 1;
        for (int i = 1; i < args.length; i++) {
            Integer number = parseId(args[i]);
            if (number != null) {
                page = Math.max(1, number);
            } else {
                filter = args[i].toLowerCase(Locale.ROOT);
            }
        }
        int pending = 0;
        int approved = 0;
        int rejected = 0;
        List<Submission> shown = new ArrayList<>();
        for (Submission s : store.getSubmissions()) {
            switch (s.getStatus()) {
                case PENDING -> pending++;
                case APPROVED -> approved++;
                case REJECTED -> rejected++;
            }
            if (matches(filter, s.getStatus())) {
                shown.add(s);
            }
        }
        shown.sort(Comparator.comparingInt(Submission::getId).reversed());

        messages.send(sender, "list-header", "pending", String.valueOf(pending),
                "approved", String.valueOf(approved), "rejected", String.valueOf(rejected), "filter", filter);
        if (shown.isEmpty()) {
            messages.send(sender, "list-empty");
            return;
        }
        int pages = (shown.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.min(page, pages);
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(shown.size(), from + PAGE_SIZE);
        for (Submission s : shown.subList(from, to)) {
            Component line = Messages.legacy(messages.raw("list-line",
                            "id", String.valueOf(s.getId()),
                            "player", s.getPlayerName(),
                            "date", plugin.formatTime(s.getSubmittedAt()),
                            "status", review.statusText(s.getStatus())))
                    .clickEvent(ClickEvent.runCommand("/desenhar ver " + s.getId()))
                    .hoverEvent(HoverEvent.showText(messages.get("click-to-view")));
            sender.sendMessage(line);
        }
        messages.send(sender, "list-footer", "page", String.valueOf(page), "pages", String.valueOf(pages));
    }

    private static boolean matches(String filter, SubmissionStatus status) {
        return switch (filter) {
            case "todas", "todos" -> true;
            case "aprovadas", "aprovados" -> status == SubmissionStatus.APPROVED;
            case "rejeitadas", "rejeitados" -> status == SubmissionStatus.REJECTED;
            default -> status == SubmissionStatus.PENDING;
        };
    }

    private Submission findSubmission(CommandSender sender, String[] args, String usageKey) {
        if (args.length < 2) {
            messages.send(sender, usageKey);
            return null;
        }
        Integer id = parseId(args[1]);
        Submission submission = id == null ? null : store.getSubmission(id);
        if (submission == null) {
            messages.send(sender, "submission-not-found");
            return null;
        }
        return submission;
    }

    private void view(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Submission submission = findSubmission(sender, args, "usage-ver");
        if (submission == null) {
            return;
        }
        if (sender instanceof Player player) {
            review.open(player, submission);
            return;
        }
        sender.sendMessage(Messages.legacy(messages.raw("console-view",
                "id", String.valueOf(submission.getId()),
                "player", submission.getPlayerName(),
                "date", plugin.formatTime(submission.getSubmittedAt()),
                "status", review.statusText(submission.getStatus()))));
    }

    private void evaluate(CommandSender sender, String[] args, boolean approve) {
        if (!requireAdmin(sender)) {
            return;
        }
        Submission submission = findSubmission(sender, args, approve ? "usage-aprovar" : "usage-rejeitar");
        if (submission == null) {
            return;
        }
        String note = null;
        if (args.length > 2) {
            note = String.join(" ", List.of(args).subList(2, args.length));
        }
        if (!service.review(sender, submission, approve, note)) {
            messages.send(sender, "already-reviewed", "id", String.valueOf(submission.getId()));
            return;
        }
        messages.send(sender, approve ? "review-done-approved" : "review-done-rejected",
                "id", String.valueOf(submission.getId()), "player", submission.getPlayerName());
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        plugin.reloadSettings();
        messages.send(sender, "reloaded");
    }

    private static Integer parseId(String text) {
        try {
            return Integer.parseInt(text.replace("#", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------- tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission(HitomicDrawPlugin.PERM_ADMIN);
        if (args.length == 1) {
            options.addAll(PLAYER_SUBS);
            if (admin) {
                options.addAll(ADMIN_SUBS);
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "abrir", "receber" -> {
                    if (sender instanceof Player player) {
                        for (Drawing d : store.getActiveDrawings(player.getUniqueId())) {
                            options.add(String.valueOf(d.getId()));
                        }
                    }
                }
                case "ver", "aprovar", "rejeitar" -> {
                    if (admin) {
                        for (Submission s : store.getSubmissions()) {
                            if (s.getStatus() == SubmissionStatus.PENDING || sub.equals("ver")) {
                                options.add(String.valueOf(s.getId()));
                            }
                        }
                    }
                }
                case "avaliacoes" -> {
                    if (admin) {
                        options.addAll(FILTERS);
                    }
                }
                default -> {
                }
            }
            return filter(options, args[1]);
        }
        return options;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
