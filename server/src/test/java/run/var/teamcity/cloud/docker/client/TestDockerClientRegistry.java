package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;


public class TestDockerClientRegistry implements DockerRegistryClient {

    private final ReentrantLock lock = new ReentrantLock();
    private DockerClientProcessingException failOnAccessException = null;

    private Set<TestImage> knownImages = new HashSet<>();


    private Map<UUID, String> loginTokenToScopes = new HashMap<>();
    private boolean closed = false;

    @NotNull
    @Override
    public Node anonymousLogin(@NotNull String scope) {

        TestUtils.waitMillis(300);
        lock.lock();
        try {
            checkForFailure();
            UUID token = UUID.randomUUID();
            loginTokenToScopes.put(token, scope);
            return Node.EMPTY_OBJECT.editNode().put("token", token.toString()).saveNode();
        } finally {
            lock.unlock();
        }
    }

    @NotNull
    @Override
    public Node listTags(@NotNull String loginToken, @NotNull String repo) {
        TestUtils.waitMillis(300);

        lock.lock();
        try {
            String scope = loginTokenToScopes.get(UUID.fromString(loginToken));
            if (scope == null || !scope.equals("repository:" + repo+ ":pull")) {
                throw new InvocationFailedException("Test authorization failed. Not in scope: " + repo);
            }

            EditableNode result = Node.EMPTY_OBJECT.editNode();
            EditableNode tags = result.getOrCreateArray("tags");
            knownImages.stream().
                    filter(img -> img.getRepo().equals(repo)).
                    map(TestImage::getTag).
                    forEach(tags::add);
            return result.saveNode();
        } finally {
            lock.unlock();
        }
    }

    public TestDockerClientRegistry knownImage(String repo, String... tags) {
        for (String tag : tags) {
            knownImages.add(new TestImage(repo, tag));
        }
        return this;
    }

    public TestDockerClientRegistry failOnAccess(DockerClientProcessingException failOnAccess) {
        this.failOnAccessException = failOnAccess;
        return this;
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
        } finally {
            lock.unlock();
        }
    }

    private void checkForFailure() {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Client registry has been closed.");
            }
            if (failOnAccessException != null) {
                throw failOnAccessException;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
