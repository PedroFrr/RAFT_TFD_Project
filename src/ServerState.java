public class ServerState {

    private int term = 0; //latest term server has seen (initialized to 0 on first boot, increases monotonically)
    private int leaderCommit = 0;
    private int commitIndex = -1; //index of highest log entry known to be committed (initialized to 0, increases monotonically)
    private int lastApplied = 0; //index of highest log entry applied to state machine (initialized to 0, increases monotonically)
    private int leaderId;
    private int votedFor = 0; //candidateId that received vote in currentterm (or null if none). [Who I've votedFor]
    private ServerStatus serverStatus; //server status; [leader, candidate, follower]
    private int serverId;
    private int[] nextIndex;
    private int[] matchIndex;

    public int[] getNextIndex() {
        return nextIndex;
    }

    public void setNextIndex(int[] nextIndex) {
        this.nextIndex = nextIndex;
    }

    public int[] getMatchIndex() {
        return matchIndex;
    }

    public void setMatchIndex(int[] matchIndex) {
        this.matchIndex = matchIndex;
    }

    public static final String SETTINGS_FILE = "server.ini";

    public Integer getCommitIndex(){
        return commitIndex;
    }

    public void setCommitIndex(Integer commitIndex){
        this.commitIndex=commitIndex;
    }

    public Integer getLastApplied(){
        return lastApplied;
    }

    public void setLastApplied(Integer lastApplied){
        this.lastApplied=lastApplied;
    }


    public Integer getTerm(){
        return term;
    }

    public void setTerm(int term){
        this.term =term;
    }

    public Integer getVotedFor(){ return votedFor; }

    public void setVotedFor(int votedFor){
        this.votedFor=votedFor;
    }

    public Integer getLeaderId(){
        return leaderId;
    }

    public void setLeaderId(int leaderId){
        this.leaderId=leaderId;
    }

    public ServerStatus getServerStatus(){
        return serverStatus;
    }

    public void setServerStatus(ServerStatus serverStatus){ this.serverStatus = serverStatus; }

    public Integer getLeaderCommit(){
        return leaderCommit;
    }

    public void setLeaderCommit(int leaderCommit){
        this.leaderCommit=leaderCommit;
    }

    public Integer getServerId(){
        return serverId;
    }

    public void setServerId(int serverId){
        this.serverId=serverId;
    }

}