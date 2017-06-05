package run.var.teamcity.cloud.docker;

import javax.annotation.Nonnull;
import java.math.BigInteger;

public interface PullStatusListener {

    int NO_PROGRESS = -1;

    void pullInProgress(@Nonnull String layer, String status, int percent);
}
