package net.stormdev.mario.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.stormdev.mario.mariokart.MarioKart;
import net.stormdev.mario.tracks.RaceTrack;
import net.stormdev.mario.utils.MetaValue;
import net.stormdev.mario.utils.ObjectWrapper;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

public class VoteHandler {
	
	private final String VOTE_META;
	private final String VOTE_META_KEY = "mariokart.vote";
	private final String VOTE_MESSAGE = ChatColor.GOLD+"Use \"/mvote <TrackName>\" to cast your vote!";
	private static final int VOTE_TIME = 120;
	private Map<String, Integer> votes = new HashMap<String, Integer>();
	private Scoreboard board;
	private Objective obj;
	private boolean closed = false;
	private BukkitTask voteCountdown = null;
	private long startTime;
	OfflinePlayer line1;
	OfflinePlayer line2;
	private List<String> maps;
	
	public VoteHandler(){
		startTime = System.currentTimeMillis();
		VOTE_META = UUID.randomUUID().toString();
		board = Bukkit.getScoreboardManager().getNewScoreboard();
		obj = board.registerNewObjective("votes", "dummy");
		obj.setDisplayName(ChatColor.BOLD+""+ChatColor.RED+"Votes:");
		obj.setDisplaySlot(DisplaySlot.SIDEBAR);
		calculateMapList();
		
		Collection<? extends Player> online = Bukkit.getOnlinePlayers();
		for(Player p:online){
			addPlayerToBoard(p);
			p.sendMessage(ChatColor.BOLD+""+ChatColor.DARK_RED+"------------------------------");
			p.sendMessage(getHelpString());
			p.sendMessage(getAvailTracksString());
			p.sendMessage(ChatColor.BOLD+""+ChatColor.DARK_RED+"------------------------------");
			try {
				bossBar(p);
			} catch (Exception e) {
				//OH WELL
			}
		}
		
		line1 = Bukkit.getOfflinePlayer(ChatColor.GRAY+"Nobody has");
		line2 = Bukkit.getOfflinePlayer(ChatColor.GRAY+"voted yet!");
		
		obj.getScore(line1)
			.setScore(-1);
		obj.getScore(line2)
			.setScore(-2);
		
		
		voteCountdown = Bukkit.getScheduler().runTaskTimer(MarioKart.plugin, new Runnable(){

			@Override
			public void run() {
				try {
					int i = getVoteTimeRemaining();
					obj.setDisplayName(ChatColor.BOLD+""+ChatColor.RED+"Votes: ("+i+")");
					if(i <= 0){
						//END VOTE
						voteCountdown.cancel();
						closeVotes();
					}
				} catch (Exception e) {
					return;
				}
				return;
			}}, 20l, 20l);
	}
	
	private void calculateMapList(){
		if(maps != null){
			maps.clear();
		}
		else {
			maps = new ArrayList<String>();
		}
		List<RaceTrack> all = new ArrayList<RaceTrack>(MarioKart.plugin.trackManager.getRaceTracks());
		if(all.size() <= 5){
			for(RaceTrack track:all){
				maps.add(track.getTrackName());
			}
			return;
		}
		while(maps.size() < 5){
			RaceTrack rand = all.get(MarioKart.plugin.random.nextInt(all.size()));
			if(maps.contains(rand.getTrackName())){
				continue;
			}
			maps.add(rand.getTrackName());
		}
	}
	
	public boolean isBeingVotedOn(String track){
		return maps.contains(track);
	}
	
	public void bossBar(final Player player){
		
		final ObjectWrapper<BukkitTask> o = new ObjectWrapper<BukkitTask>();
		o.setValue(Bukkit.getScheduler().runTaskTimerAsynchronously(MarioKart.plugin, new Runnable(){

			@Override
			public void run() {
				if(closed){
					BossBar.removeBar(player);
					o.getValue().cancel();
					return;
				}
				if(!FullServerManager.get().getStage().equals(ServerStage.WAITING)){
					o.getValue().cancel();
					return;
				}
				final float percent = (((float)(getVoteTimeRemaining())/(float)(getTotalTime()))*100);
				final int rem = getVoteTimeRemaining();
				Bukkit.getScheduler().runTask(MarioKart.plugin, new Runnable(){

					@Override
					public void run() {
						if(!BossBar.hasBar(player)){
							BossBar.setMessage(player, VOTE_MESSAGE, percent);
						}
						else {
							BossBar.setHealth(player, percent);
						}
						
						player.setLevel(rem);
						if(rem < 20){
							player.playSound(player.getLocation(), Sound.BLOCK_NOTE_PLING, 1f, Integer.MAX_VALUE);
						}
					}});
				return;
			}}, 20l, 20l));
	}
	
	public int getTotalTime(){
		int fullS = VOTE_TIME;
		int online = Bukkit.getOnlinePlayers().size();
		if(online < 2){
			fullS = VOTE_TIME;
		}
		else if(online <= 2){
			fullS = (int) (0.7*fullS);
		}
		else if(online < 4){
			fullS = (int) (0.6*fullS);
		}
		else if(online > 3 && online < 6){
			fullS = (int) (0.5*fullS);
		}
		else if(online > 6 && online < 10){
			fullS = (int) (0.25*fullS);
		}
		else if(online > 10){
			fullS = (int) (0.2*fullS);
		}
		return fullS;
	}
	
	public int getVoteTimeRemaining(){
		int fullS = getTotalTime();
		if(Bukkit.getOnlinePlayers().size() < 1){
			startTime = System.currentTimeMillis();
			return fullS;
		}
		
		long diff = System.currentTimeMillis() - startTime;
		int rem = (int) ((fullS*1000)-diff)/1000;
		if(rem < 0){
			rem = 0;
		}
		return rem;
	}
	
	public void closeVotes(){
		if(closed){
			return;
		}
		closed = true;
		Collection<? extends Player> online = Bukkit.getOnlinePlayers();
		for(Player p:online){
			removePlayerFromBoard(p);
		}
		
		obj.unregister();
		
		String tName = null;
		int i = -1;
		if(votes.size() > 0){
			List<String> keys = new ArrayList<String>(votes.keySet());
			for(String key:keys){
				int z = votes.get(key);
				if(z>i){
					i = z;
					tName = key;
				}
			}
			if(tName == null){
				Bukkit.broadcastMessage("No tracks setup! Please setup some tracks and restart the server!");
				return;
			}
		}
		else {
			try {
				tName = MarioKart.plugin.trackManager.getRaceTrackNames().get(
						MarioKart.plugin.random.nextInt(
								MarioKart.plugin.trackManager.getRaceTrackNames().size()));
			} catch (Exception e) {
				//No tracks setup
				Bukkit.broadcastMessage("No tracks setup! Please setup some tracks and restart the server!");
				return;
			}
		}
		FullServerManager.get().trackSelected(tName);
	}
	
	public void removePlayerFromBoard(final Player player){
		player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
		
		Bukkit.getScheduler().runTask(MarioKart.plugin, new Runnable(){

			@Override
			public void run() {
				if(BossBar.hasBar(player)){
					BossBar.removeBar(player);
				}
				return;
			}});
	}
	
	public void addPlayerToBoard(Player player){
		player.setScoreboard(board);
		bossBar(player);
	}
	
	public String getHelpString(){
		return ChatColor.BOLD+""+ChatColor.GOLD+"Votes are open! Do \"/mvote <TrackName>\" to vote to race on your favourite track!";
	}
	
	public String getAvailTracksString(){
		StringBuilder avail = new StringBuilder(ChatColor.BOLD+""+ChatColor.DARK_RED+"Available tracks: ");
		List<String> tracks = maps;
		boolean f = true;
		for(String t:tracks){
			if(f){
				avail.append(ChatColor.WHITE+t);
				f = false;
				continue;
			}
			avail.append(ChatColor.GOLD).append(", ").append(ChatColor.WHITE).append(t);
		}
		return avail.toString();
	}
	
	private synchronized void incrementVote(String tName){
		board.resetScores(line1);
		board.resetScores(line2);
		
		int score = 0;
		if(votes.containsKey(tName)){
			score = votes.get(tName);
		}
		score++;
		votes.put(tName, score);
		
		String sName = ChatColor.GOLD+tName;
		if(sName.length() > 16){
			sName = sName.substring(0, 16);
		}
		
		Score sscore = obj.getScore(Bukkit.getOfflinePlayer(sName));
		sscore.setScore(score);
	}
	
	public boolean castVote(Player player, String trackName){
		if(closed){
			player.sendMessage(ChatColor.RED+"Sorry, track voting has closed");
			return false;
		}
		if(hasVoted(player)){
			player.sendMessage(ChatColor.RED+"You have already voted!");
			return false;
		}
		if(!MarioKart.plugin.trackManager.raceTrackExists(trackName)){
			player.sendMessage(ChatColor.RED+"That track doesn't exist! ("+trackName+")");
			player.sendMessage(getAvailTracksString());
			return false;
		}
		final String name = MarioKart.plugin.trackManager.getRaceTrack(trackName).getTrackName();
		if(!isBeingVotedOn(name)){
			player.sendMessage(ChatColor.RED+"That track is not being voted on, sorry.");
			return false;
		}
		Bukkit.getScheduler().runTaskAsynchronously(MarioKart.plugin, new Runnable(){

			@Override
			public void run() {
				incrementVote(name);
				return;
			}});
		player.setMetadata(VOTE_META_KEY, new MetaValue(VOTE_META, MarioKart.plugin));
		player.sendMessage(ChatColor.GREEN+"Cast your vote!");
		return true;
	}
	
	public boolean hasVoted(Player player){
		if(!player.hasMetadata(VOTE_META_KEY)){
			return false;
		}
		Object o = player.getMetadata(VOTE_META_KEY).get(0).value();
		boolean has = VOTE_META.equals(o.toString());
		if(!has){
			player.removeMetadata(VOTE_META_KEY, MarioKart.plugin);
		}
		return has;
	}
}
