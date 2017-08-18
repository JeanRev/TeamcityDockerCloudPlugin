package run.var.teamcity.cloud.docker.test;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.support.AbstractApplicationContext;

import java.util.UUID;

public class TestApplicationContext extends AbstractApplicationContext {

    private final ConfigurableListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    @Override
    protected void refreshBeanFactory() throws BeansException, IllegalStateException {
        // Nothing to do.
    }

    @Override
    protected void closeBeanFactory() {
        // Nothing to do.
    }

    @Override
    public ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException {
        return beanFactory;
    }

    public void registerBean(Class<?> beanClass) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(beanClass);
        ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(UUID.randomUUID().toString(), beanDefinition);
    }


}
