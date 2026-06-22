@EventHandler
public void onJoin(PlayerJoinEvent event) {

    Player player = event.getPlayer();

    player.setScoreboard(
        Bukkit.getScoreboardManager().getNewScoreboard()
    );

    nametagManager.updateAll();
}
