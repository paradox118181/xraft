package in.xnnyygn.xraft.core.serverstate;

import in.xnnyygn.xraft.core.schedule.ElectionTimeout;
import in.xnnyygn.xraft.core.rpc.AppendEntriesResult;
import in.xnnyygn.xraft.core.rpc.AppendEntriesRpc;
import in.xnnyygn.xraft.core.rpc.RequestVoteResult;
import in.xnnyygn.xraft.core.rpc.RequestVoteRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CandidateServerState extends AbstractServerState {

    private static final Logger logger = LoggerFactory.getLogger(CandidateServerState.class);
    private final int votedCount;
    private final ElectionTimeout electionTimeout;

    public CandidateServerState(int term, ElectionTimeout electionTimeout) {
        this(term, 1, electionTimeout);
    }

    public CandidateServerState(int term, int votedCount, ElectionTimeout electionTimeout) {
        super(ServerRole.CANDIDATE, term);
        this.votedCount = votedCount;
        this.electionTimeout = electionTimeout;
    }

    @Override
    public ServerStateSnapshot takeSnapshot() {
        ServerStateSnapshot snapshot = new ServerStateSnapshot(this.role, this.term);
        snapshot.setVotesCount(this.votedCount);
        return snapshot;
    }

    @Override
    protected void cancelTimeoutOrTask() {
        this.electionTimeout.cancel();
    }

    @Override
    public void onReceiveRequestVoteResult(ServerStateContext context, RequestVoteResult result) {
        if (result.isVoteGranted()) {
            int votesCount = this.votedCount + 1;
            if (votesCount > (context.getServerCount() / 2)) {
                this.electionTimeout.cancel();
                context.setServerState(new LeaderServerState(this.term, context.scheduleLogReplicationTask()));
            } else {
                context.setServerState(new CandidateServerState(this.term, votedCount, electionTimeout.reset()));
            }
        } else if (result.getTerm() > this.term) {
            logger.debug("Server {}, update to peer's term", context.getSelfServerId(), result.getTerm());

            // current term is old
            context.setServerState(new FollowerServerState(result.getTerm(), null, null, electionTimeout.reset()));
        }
    }

    @Override
    protected RequestVoteResult processRequestVoteRpc(ServerStateContext context, RequestVoteRpc rpc) {

        // voted for self
        return new RequestVoteResult(this.term, false);
    }

    @Override
    protected AppendEntriesResult processAppendEntriesRpc(ServerStateContext context, AppendEntriesRpc rpc) {
        // more than 1 candidate but another server win the election
        context.setServerState(new FollowerServerState(this.term, null, rpc.getLeaderId(), electionTimeout.reset()));
        return new AppendEntriesResult(this.term, true);
    }

    @Override
    public String toString() {
        return "CandidateServerState{" +
                electionTimeout +
                ", term=" + term +
                ", votedCount=" + votedCount +
                '}';
    }

}