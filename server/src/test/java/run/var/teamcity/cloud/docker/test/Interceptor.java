package run.var.teamcity.cloud.docker.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class Interceptor<I> {

    private final Object wrappedObject;
    private final Class<I> iface;

    private Runnable beforeInvoke;

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
            try {
                return method.invoke(wrappedObject, args);
            } catch (IllegalAccessException e) {
                throw e;
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        });
    }

    public static <T extends I, I> Interceptor<I> wrap(T object, Class<I> iface) {
        return new Interceptor<>(object, iface);
    }
}
