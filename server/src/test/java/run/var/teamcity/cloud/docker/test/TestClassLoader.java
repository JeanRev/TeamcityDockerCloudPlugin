package run.var.teamcity.cloud.docker.test;

public class TestClassLoader extends ClassLoader {

    private String queriedClass = null;

    @Override
    protected synchronized Class<?> findClass(String name) throws ClassNotFoundException {

        queriedClass = name;

        throw new ClassNotFoundException(name);
    }

    public synchronized String getQueriedClass() {
        return queriedClass;
    }
}
