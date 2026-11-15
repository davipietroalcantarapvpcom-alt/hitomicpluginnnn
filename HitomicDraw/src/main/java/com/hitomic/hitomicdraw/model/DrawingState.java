package com.hitomic.hitomicdraw.model;

/** Ciclo de vida de um desenho. */
public enum DrawingState {
    /** Em edicao pelo dono. */
    DRAFT,
    /** Finalizado, aguardando o dono dropar o mapa para enviar. */
    FINISHED,
    /** Enviado para avaliacao; bloqueado para qualquer alteracao. */
    SUBMITTED
}
