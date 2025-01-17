package io.dropwizard.testing.junit5;

import io.dropwizard.testing.app.TestEntity;
import org.hibernate.Session;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(DropwizardExtensionsSupport.class)
class DAOTestExtensionConfigTest {
    private final DAOTestExtension database = DAOTestExtension.newBuilder()
        .setUrl("jdbc:h2:mem:rule-config-test")
        .setDriver(org.h2.Driver.class)
        .setUsername("username")
        .useSqlComments(true)
        .setHbm2DdlAuto("create")
        .setShowSql(true)
        .addEntityClass(TestEntity.class)
        .setProperty(AvailableSettings.FORMAT_SQL, "true")
        .build();

    @Test
    void explicitConfigCreatesSessionFactory() {
        // it yields a valid SessionFactory instance
        assertThat(database.getSessionFactory()).isNotNull();

        final Session currentSession = database.getSessionFactory().getCurrentSession();

        // an instance of an entity contained in the package can be saved
        currentSession.persist(new TestEntity("foo"));
    }
}
