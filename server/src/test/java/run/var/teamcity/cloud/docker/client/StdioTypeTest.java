package run.var.teamcity.cloud.docker.client;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link StdioType} test suite.
 */
public class StdioTypeTest {

    @Test
    public void streamTypeLookup() {
        List<StdioType> types = Arrays.stream(StdioType.values()).
                map(StdioType::streamType).
                map(StdioType::fromStreamType).
                collect(Collectors.toList());

        assertThat(types).contains(StdioType.values());

    }

    @Test
    public void noDuplicateStreamType() {
        List<Long> types = Arrays.stream(StdioType.values()).
                map(StdioType::streamType).
                collect(Collectors.toList());

        assertThat(types).doesNotHaveDuplicates();
    }

    @Test
    public void unknownStreamType() {
        long unknownType = -42;
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                StdioType.fromStreamType(unknownType));
    }
}