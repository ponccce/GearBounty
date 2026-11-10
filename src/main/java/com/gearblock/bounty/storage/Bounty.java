package com.gearblock.bounty.storage;

import java.util.UUID;

/** One contribution: a player (or the server) put {@code amount} on {@code target}'s head. */
public record Bounty(long id, UUID target, String targetName, UUID placer, String placerName, double amount, long createdAt) {

    /** Placer id used for bounties created by an admin from the console. No real player has it. */
    public static final UUID SERVER = new UUID(0L, 0L);
    public static final String SERVER_NAME = "Server";

    public boolean fromServer() {
        return SERVER.equals(placer);
    }
}
