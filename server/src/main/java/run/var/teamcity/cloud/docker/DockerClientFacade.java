package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientException;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;

/**
 * Facade to manage dockerized agent instances. Instances of this class can be obtained using
 * a {@link DockerClientFacadeFactory}.
 * <p>
 *     This class abstracts away the interactions with the {@link DockerClient} by providing high-level, atomic
 *     operations, to handle agent containers.
 * </p>
 * <p>
 *     Different types of facade may support different of orchestration. The client is responsible to provide Docker
 *     object specifications that match the type of facade requested at instantiation time.
 * </p>
 * <p>
 *     Instances of this class are thread-safe.
 * </p>
 */
public interface DockerClientFacade extends AutoCloseable {

    /**
     * Creates a new, unstarted, agent.
     *
     * @param createParameters parameters for creating the agent
     *
     * @return the information related to the created agent holder
     *
     * @throws NullPointerException if {@code createParameters} is {@code null}
     * @throws IllegalArgumentException if the some of the creation parameters are not valid for this facade
     * implementation
     * @throws DockerClientException if an error occurred while interacting with the Docker daemon
     * @throws DockerClientFacadeException if an error occurred while processing the creation parameters or the daemon
     * response
     */
    @Nonnull
    NewAgentHolderInfo createAgent(@Nonnull CreateAgentParameters createParameters);

    /**
     * Starts the agent owned by the given agent holder id.
     *
     * @param agentHolderId the agent holder id
     *
     * @throws NullPointerException if the {@code agentHolderId} is {@code null}
     * @throws DockerClientException if an error occurred while interacting with the Docker daemon
     * @throws DockerClientFacadeException if an error occurred while processing the creation parameters or the daemon
     * response
     */
    String startAgent(@Nonnull String agentHolderId);

    /**
     * Restarts the agent owned by the given agent holder id.
     *
     * @param agentHolderId the agent holder id
     *
     * @throws NullPointerException if the {@code agentHolderId} is {@code null}
     * @throws DockerClientException if an error occurred while interacting with the Docker daemon
     * @throws DockerClientFacadeException if an error occurred while processing the creation parameters or the daemon
     * response
     */
    String restartAgent(@Nonnull String agentHolderId);

    /**
     * Lists the agent filtered with the given label key and value.
     *
     * @param labelFilter the label key
     * @param valueFilter the label value
     *
     * @return the list of agent holders with the given label set
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws DockerClientException if an error occurred while interacting with the Docker daemon
     * @throws DockerClientFacadeException if an error occurred while processing the creation parameters or the daemon
     * response
     */
    @Nonnull
    List<AgentHolderInfo> listAgentHolders(@Nonnull String labelFilter, @Nonnull String valueFilter);

    /**
     * Terminates the agent holder with the given id.
     *
     * @param agentHolderId the agent holder id
     * @param timeout timeout before the agent holder is forcibly stopped
     * @param removeContainer when {@code true} will remove the corresponding container
     *
     * @return {@code true} if the stopped container still exists after this method has been invoked
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws DockerClientException if an error occurred while interacting with the Docker daemon
     * @throws DockerClientFacadeException if an error occurred while processing the creation parameters or the daemon
     * response
     */
    boolean terminateAgentContainer(@Nonnull String agentHolderId, @Nonnull Duration timeout, boolean removeContainer);

    /**
     * Retrieves the logs for the give agent holder.
     *
     * @param agentHolderId the agent container id
     *
     * @return the agent holder logs
     *
     * @throws NullPointerException if {@code agentHolderId} is {@code null}
     * @throws DockerClientException if an error occurred while interacting with the Docker daemon
     * @throws DockerClientFacadeException if an error occurred while processing the creation parameters or the daemon
     * response
     */
    @Nonnull
    CharSequence getLogs(@Nonnull String agentHolderId);

    /**
     * Stream the logs for the given agent holder.
     *
     * @param agentHolderId the agent container id
     *
     * @return the stream handler to fetch the agent holder logs
     *
     * @throws NullPointerException if {@code agentHolderId} is {@code null}
     * @throws DockerClientException if an error occurred while interacting with the Docker daemon
     * @throws DockerClientFacadeException if an error occurred while processing the creation parameters or the daemon
     * response
     */
    @Nonnull
    StreamHandler streamLogs(@Nonnull String agentHolderId);

    /**
     * Closes the facade and the underlying Docker client. This method has no effect if the Docker client is already
     * closed.
     */
    @Override
    void close();
}
