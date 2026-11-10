package com.gearblock.bounty.storage;

import java.util.UUID;

/** One contribution: a player put {@code amount} on {@code target}'s head. */
public record Bounty(long id, UUID target, String targetName, UUID placer, String placerName, double amount, long createdAt) {
}
