package me.vanillabounties.model;

public record AutoBountyFolder(
    long id,
    int threshold,
    String name,
    boolean protectedFolder,
    int templateCount
) {
    public boolean onKill() {
        return threshold == 0;
    }
}
