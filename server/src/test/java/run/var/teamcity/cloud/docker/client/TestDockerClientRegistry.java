package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
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

    @Nonnull
    @Override
    public Node anonymousLogin(@Nonnull String scope) {

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

    @Nonnull
    @Override
    public Node listTags(@Nonnull String loginToken, @Nonnull String repo) {
        TestUtils.waitMillis(300);

        lock.lock();
        try {
            String scope = loginTokenToScopes.get(UUID.fromString(loginToken));
            if (scope == null || !scope.equals("repository:" + repo + ":pull")) {
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
        lock.lock();
        try {
            for (String tag : tags) {
                knownImages.add(new TestImage(repo, tag));
            }
        } finally {
            lock.unlock();
        }

        return this;
    }

    public TestDockerClientRegistry failOnAccess(DockerClientProcessingException failOnAccess) {
        lock.lock();
        try {
            this.failOnAccessException = failOnAccess;
            return this;
        } finally {
            lock.unlock();
        }
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
        lock.lock();
        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }
}
