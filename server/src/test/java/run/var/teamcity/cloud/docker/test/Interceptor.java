package run.var.teamcity.cloud.docker.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Interceptor<I> {

    private final Object wrappedObject;
    private final Class<I> iface;

    private Runnable beforeInvoke;
    private List<RecordedInvocation> invocations = new CopyOnWriteArrayList<>();

    private Interceptor(Object wrappedObject, Class<I> iface) {
        assert wrappedObject != null;
        this.wrappedObject = wrappedObject;
        this.iface = iface;
    }

    public Interceptor<I> beforeInvoke(Runnable runnable) {
        this.beforeInvoke = runnable;
        return this;
    }

    @SuppressWarnings("unchecked")
    public I buildProxy() {
        return (I) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{iface}, (proxy, method, args) -> {
            if (beforeInvoke != null) {
                beforeInvoke.run();
            }
            invocations.add(new RecordedInvocation(method, args));
            try {
                return method.invoke(wrappedObject, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        });
    }

    public List<RecordedInvocation> getInvocations() {
        return Collections.unmodifiableList(invocations);
    }

    public static <T extends I, I> Interceptor<I> wrap(T object, Class<I> iface) {
        return new Interceptor<>(object, iface);
    }

    public class RecordedInvocation {
        private final Method method;
        private final Object[] args;

        private RecordedInvocation(Method method, Object[] args) {
            this.method = method;
            this.args = args;
        }

        public Method getMethod() {
            return method;
        }

        public Object[] getArgs() {
            return args;
        }

        public boolean matches(String methodName, Object... args) {
            return method.getName().equals(methodName) && Arrays.deepEquals(this.args, args);
        }
    }
}