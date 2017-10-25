package run.var.teamcity.cloud.docker.util;

import org.junit.Test;
import run.var.teamcity.cloud.docker.TestResourceBundle;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.*;

public class ResourcesTest {

    @Test
    public void invalidConstructorInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new Resources((ResourceBundle[]) null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(Resources::new);
    }

    @Test
    public void text() {
        TestResourceBundle bundle = new TestResourceBundle();

        bundle.getResourcesMap().put("test.key", "{0,number,#} {1,number} {3} '{4}' {5}");

        Resources resources = new Resources(bundle);

        assertThat(resources.text("test.key", 1000, 1000, 1000, null)).isEqualTo("1000 1,000 null {4} {5}");
    }

    @Test
    public void textMultipleBundles() {
        TestResourceBundle bundle1 = new TestResourceBundle();
        TestResourceBundle bundle2 = new TestResourceBundle();
        TestResourceBundle bundle3 = new TestResourceBundle();

        bundle1.getResourcesMap().put("A", "1");
        bundle2.getResourcesMap().put("A", "2");
        bundle3.getResourcesMap().put("A", "3");

        bundle2.getResourcesMap().put("B", "2");
        bundle3.getResourcesMap().put("B", "3");

        Resources resources = new Resources(bundle1, bundle2, bundle3);

        assertThat(resources.text("A", new Object[0])).isEqualTo("1");
        assertThat(resources.text("B", new Object[0])).isEqualTo("2");
    }

    @Test
    public void textNoArg() {
        TestResourceBundle bundle = new TestResourceBundle();

        bundle.getResourcesMap().put("test.key", "'foo'");

        Resources resources = new Resources(bundle);

        assertThat(resources.text("test.key")).isEqualTo("foo");
    }

    @Test
    public void textNoArgMultipleBundles() {
        TestResourceBundle bundle1 = new TestResourceBundle();
        TestResourceBundle bundle2 = new TestResourceBundle();
        TestResourceBundle bundle3 = new TestResourceBundle();

        bundle1.getResourcesMap().put("A", "1");
        bundle2.getResourcesMap().put("A", "2");
        bundle3.getResourcesMap().put("A", "3");

        bundle2.getResourcesMap().put("B", "2");
        bundle3.getResourcesMap().put("B", "3");

        Resources resources = new Resources(bundle1, bundle2, bundle3);

        assertThat(resources.text("A")).isEqualTo("1");
        assertThat(resources.text("B")).isEqualTo("2");
    }

    @Test
    public void textInvalidInput() {
        TestResourceBundle bundle = new TestResourceBundle();

        bundle.getResourcesMap().put("test.key", "foo");

        Resources resources = new Resources(bundle);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> resources.text(null, new Object[0]));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> resources.text("missing.key",
                (Object[]) null));
        assertThatExceptionOfType(MissingResourceException.class).isThrownBy(() -> resources.text("missing.key",
                new Object[0]));
    }

    @Test
    public void textNoArgInvalidInput() {
        TestResourceBundle bundle = new TestResourceBundle();

        bundle.getResourcesMap().put("test.key", "foo");

        Resources resources = new Resources(bundle);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> resources.text(null));
        assertThatExceptionOfType(MissingResourceException.class).isThrownBy(() -> resources.text("missing.key"));
    }

    @Test
    public void string() {
        TestResourceBundle bundle = new TestResourceBundle();

        bundle.getResourcesMap().put("test.key", "'foo'");

        Resources resources = new Resources(bundle);

        assertThat(resources.string("test.key")).isEqualTo("'foo'");
    }

    @Test
    public void stringMultipleBundles() {
        TestResourceBundle bundle1 = new TestResourceBundle();
        TestResourceBundle bundle2 = new TestResourceBundle();
        TestResourceBundle bundle3 = new TestResourceBundle();

        bundle1.getResourcesMap().put("A", "1");
        bundle2.getResourcesMap().put("A", "2");
        bundle3.getResourcesMap().put("A", "3");

        bundle2.getResourcesMap().put("B", "2");
        bundle3.getResourcesMap().put("B", "3");

        Resources resources = new Resources(bundle1, bundle2, bundle3);

        assertThat(resources.string("A")).isEqualTo("1");
        assertThat(resources.string("B")).isEqualTo("2");
    }

    @Test
    public void stringInvalidInput() {
        TestResourceBundle bundle = new TestResourceBundle();

        bundle.getResourcesMap().put("test.key", "'foo'");

        Resources resources = new Resources(bundle);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> resources.string(null));
        assertThatExceptionOfType(MissingResourceException.class).isThrownBy(() -> resources.string("missing.key"));
    }
}