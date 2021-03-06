package ut.com.carolynvs.stash.plugin.reject_merge_commit_hook;

import com.atlassian.bitbucket.commit.*;
import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.idx.CommitIndex;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.git.command.*;
import com.carolynvs.stash.plugin.reject_merge_commit_hook.GitBranchListOutputHandler;
import com.carolynvs.stash.plugin.reject_merge_commit_hook.RejectMergeCommitHook;
import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.*;
import org.junit.Test;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.OngoingStubbing;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * Testing {@link com.carolynvs.stash.plugin.reject_merge_commit_hook.RejectMergeCommitHook}
 */
@RunWith(MockitoJUnitRunner.class)
public class RejectMergeCommitHookTest extends TestCase
{
    @Mock
    private CommitService commitService;

    @Mock
    private CommitIndex commitIndex;

    @Mock
    private Repository repository;

    @Mock
    private GitCommandBuilderFactory commandFactory;

    @Mock
    private I18nService i18nService;

    @Mock
    private RepositoryHookContext hookContext;

    @Mock
    private HookResponse hookResponse;

    @Before
    public void Setup()
    {
        MockRepository();
        MockHookResponse();
        MockCommitIndex();
    }

    private void MockRepository()
    {
        when(hookContext.getRepository()).thenReturn(repository);
    }

    private void MockHookResponse()
    {
        StringWriter output = new StringWriter();
        when(hookResponse.err()).thenReturn(new PrintWriter(output));
    }

    private void MockGitBranchContainsCommand(String... branches)
    {
        GitCommand<List<String>> gitBranchesCommand = (GitCommand<List<String>>) mock(GitCommand.class);
        OngoingStubbing<List<String>> when = when(gitBranchesCommand.call());
        for (String branch : branches)
        {
            ArrayList<String> result = new ArrayList<String>();
            if(branch != null)
                result.add(branch);
            when = when.thenReturn(result);
        }

        GitScmCommandBuilder gitCommandBuilder = mock(GitScmCommandBuilder.class);
        when(gitCommandBuilder.command(any(String.class))).thenReturn(gitCommandBuilder);
        when(gitCommandBuilder.argument(any(String.class))).thenReturn(gitCommandBuilder);
        when(gitCommandBuilder.build(any(GitBranchListOutputHandler.class))).thenReturn(gitBranchesCommand);

        when(commandFactory.builder(repository)).thenReturn(gitCommandBuilder);
    }

    private void MockCommit(final Commit commit)
    {
        final String commitId = commit.getId();

        when(commitService.getCommit(argThat(new ArgumentMatcher<CommitRequest>() {
            @Override
            public boolean matches(CommitRequest request) {
                return request != null && request.getCommitId().equals(commitId);
            }
        }))).thenReturn(commit);
    }

    private void MockCommitIndex()
    {
        when(commitIndex.isIndexed(any(String.class), any(Repository.class))).thenReturn(false);
    }

    @Test
    public void WhenCommit_WithMergeFromMasterToMaster_IsPushedToMaster_ItIsRejected()
    {
        Commit mergeCommit = buildMergeCommit();
        MockGitBranchContainsCommand("master", null);

        RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/master", mergeCommit.getId(), mergeCommit.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertFalse(isAccepted);
    }

    @Test
    public void WhenCommit_WithMergeFromTrunkToFeature_IsPushedToMaster_ItIsAccepted()
    {
        Commit mergeCommit = buildMergeCommit();
        MockGitBranchContainsCommand("feature-branch", null);

        RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/master", mergeCommit.getId(), mergeCommit.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertTrue(isAccepted);
    }

    @Test
    public void WhenCommit_WithMergeFromTrunkToFeature_IsPushedToFeature_ItIsAccepted()
    {
        Commit mergeCommit = buildMergeCommit();
        MockGitBranchContainsCommand("master", "feature-branch");

        RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/feature-branch", mergeCommit.getId(), mergeCommit.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertTrue(isAccepted);
    }

    @Test
    public void WhenCommit_WithoutMerge_IsPushed_ItIsAccepted()
    {
        Commit normalCommit = buildCommit();

        RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/feature-branch", normalCommit.getId(), normalCommit.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertTrue(isAccepted);
    }

    private RefChange buildRefChange(String refId, String fromHash, String toHash)
    {
        RefChange refChange = mock(RefChange.class);

        when(refChange.getRefId()).thenReturn(refId);
        when(refChange.getToHash()).thenReturn(toHash);

        return refChange;
    }

    private Commit buildMergeCommit()
    {
        return buildCommit(2);
    }

    private Commit buildCommit()
    {
        return buildCommit(1);
    }

    private Commit buildCommit(int numberOfParents)
    {
        Commit commit = mock(Commit.class);

        when(commit.getId()).thenReturn(UUID.randomUUID().toString());

        ArrayList<MinimalCommit> parents = new ArrayList<MinimalCommit>(numberOfParents);
        for (int i = 0; i < numberOfParents; i++)
        {
            parents.add(buildParentCommit());
        }
        when(commit.getParents()).thenReturn(parents);
        MockCommit(commit);
        return commit;
    }

    //TODO: the recursion fails because the parent commit does not really exist
    private Commit buildParentCommit()
    {
        Commit parent = mock(Commit.class);

        when(parent.getId()).thenReturn(UUID.randomUUID().toString());
        MockCommit(parent);
        return parent;
    }
}