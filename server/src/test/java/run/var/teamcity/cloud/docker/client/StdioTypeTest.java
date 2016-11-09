package run.var.teamcity.cloud.docker.client;

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link StdioType} test suite.
 */
@Test
public class StdioTypeTest {

    public void streamTypeLookup() {
        List<StdioType> types = Arrays.stream(StdioType.values()).
                map(StdioType::streamType).
                map(StdioType::fromStreamType).
                collect(Collectors.toList());

        assertThat(types).contains(StdioType.values());

    }

    public void noDuplicateStreamType() {
        List<Long> types = Arrays.stream(StdioType.values()).
                map(StdioType::streamType).
                collect(Collectors.toList());

        assertThat(types).doesNotHaveDuplicates();
    }

    public void unknownStreamType() {
        long unknownType = -42;
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                StdioType.fromStreamType(unknownType));
    }
}