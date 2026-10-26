package io.speclens.model;

import java.util.List;

/** An edge in the navigation graph (Phase 3+): acting on {@code trigger} moves from one screen to another. */
public record Transition(String fromScreenId, String toScreenId, String trigger, List<Evidence> evidence) {
}
