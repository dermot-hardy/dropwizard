package io.dropwizard.testing.common;

import io.dropwizard.logging.common.BootstrapLogging;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.context.internal.ManagedSessionContext;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class DAOTest {
    @SuppressWarnings("unchecked")
    public abstract static class Builder<B extends Builder<B>> {
        private String url = "jdbc:h2:mem:" + UUID.randomUUID();
        private String username = "sa";
        private String password = "";
        private String driver = "org.h2.Driver";
        private String hbm2ddlAuto = "create";
        private boolean showSql = false;
        private boolean useSqlComments = false;
        private boolean bootstrapLogging = true;
        private final Set<Class<?>> entityClasses = new LinkedHashSet<>();
        private final Map<String, String> properties = new HashMap<>();
        private Consumer<Configuration> configurationCustomizer = c -> {
        };

        public B setUrl(String url) {
            this.url = url;
            return (B) this;
        }

        public B setUsername(String username) {
            this.username = username;
            return (B) this;
        }

        public B setPassword(String password) {
            this.password = password;
            return (B) this;
        }

        public B setDriver(Class<? extends java.sql.Driver> driver) {
            this.driver = driver.getName();
            return (B) this;
        }

        public B setDriver(String driver) {
            this.driver = driver;
            return (B) this;
        }

        public B setHbm2DdlAuto(String hbm2ddlAuto) {
            this.hbm2ddlAuto = hbm2ddlAuto;
            return (B) this;
        }

        public B setShowSql(boolean showSql) {
            this.showSql = showSql;
            return (B) this;
        }

        public B useSqlComments(boolean useSqlComments) {
            this.useSqlComments = useSqlComments;
            return (B) this;
        }

        /**
         * @since 2.0
         */
        public B bootstrapLogging(boolean value){
            bootstrapLogging = value;
            return (B) this;
        }

        public B addEntityClass(Class<?> entityClass) {
            this.entityClasses.add(entityClass);
            return (B) this;
        }

        public B setProperty(String key, String value) {
            this.properties.put(key, value);
            return (B) this;
        }

        public B customizeConfiguration(Consumer<Configuration> configurationCustomizer) {
            this.configurationCustomizer = configurationCustomizer;
            return (B) this;
        }

        protected DAOTest buildDAOTest() {
            if (bootstrapLogging) {
                BootstrapLogging.bootstrap();
            }

            final Configuration config = new Configuration();
            config.setProperty(AvailableSettings.URL, url);
            config.setProperty(AvailableSettings.USER, username);
            config.setProperty(AvailableSettings.PASS, password);
            config.setProperty(AvailableSettings.DRIVER, driver);
            config.setProperty(AvailableSettings.HBM2DDL_AUTO, hbm2ddlAuto);
            config.setProperty(AvailableSettings.SHOW_SQL, String.valueOf(showSql));
            config.setProperty(AvailableSettings.USE_SQL_COMMENTS, String.valueOf(useSqlComments));
            // Use the same configuration as in the Hibernate bundle to reduce differences between
            // testing and production environments.
            config.setProperty(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "managed");
            config.setProperty(AvailableSettings.USE_GET_GENERATED_KEYS, "true");
            config.setProperty(AvailableSettings.GENERATE_STATISTICS, "true");
            config.setProperty(AvailableSettings.ORDER_UPDATES, "true");
            config.setProperty(AvailableSettings.ORDER_INSERTS, "true");

            entityClasses.forEach(config::addAnnotatedClass);
            properties.forEach(config::setProperty);

            configurationCustomizer.accept(config);

            return new DAOTest(config.buildSessionFactory());
        }
    }

    private final SessionFactory sessionFactory;

    /**
     * Use {@link io.dropwizard.testing.junit5.DAOTestExtension#newBuilder()}
     */
    private DAOTest(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void before() throws Throwable {
        if (ManagedSessionContext.hasBind(sessionFactory)) {
            return;
        }

        final Session session = sessionFactory.openSession();
        ManagedSessionContext.bind(session);
    }

    public void after() {
        if (!ManagedSessionContext.hasBind(sessionFactory)) {
            return;
        }

        final Session currentSession = sessionFactory.getCurrentSession();
        if (currentSession.isOpen()) {
            currentSession.close();
        }
        ManagedSessionContext.unbind(sessionFactory);
    }

    /**
     * Returns the current active session factory for injecting to DAOs.
     *`
     * @return {@link SessionFactory} with an open session.
     */
    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    /**
     * Performs a call in a transaction
     *
     * @param call the call
     * @param <T>  the type of the returned result
     * @return the result of the call
     */
    public <T> T inTransaction(Callable<T> call) {
        final Session session = sessionFactory.getCurrentSession();
        final Transaction transaction = session.beginTransaction();
        try {
            final T result = call.call();
            transaction.commit();
            return result;
        } catch (final Exception e) {
            transaction.rollback();
            if (e instanceof RuntimeException runtimeException) {
              throw runtimeException;
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * Performs an action in a transaction
     *
     * @param action the action
     */
    public void inTransaction(Runnable action) {
        inTransaction(() -> {
            action.run();
            return true;
        });
    }
}
