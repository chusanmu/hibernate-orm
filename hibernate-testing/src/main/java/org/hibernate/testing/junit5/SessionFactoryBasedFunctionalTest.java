/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.testing.junit5;

import java.util.Arrays;
import java.util.EnumSet;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.Root;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.model.relational.spi.DatabaseModel;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.Action;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.Helper;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;

import org.junit.jupiter.api.AfterEach;

import org.jboss.logging.Logger;

import static org.hibernate.testing.transaction.TransactionUtil.doInHibernate;

/**
 * @author Steve Ebersole
 */
@FunctionalSessionFactoryTesting
public abstract class SessionFactoryBasedFunctionalTest
		extends BaseUnitTest
		implements SessionFactoryProducer, SessionFactoryScopeContainer {
	private static final Logger log = Logger.getLogger( SessionFactoryBasedFunctionalTest.class );

	private SessionFactoryScope sessionFactoryScope;

	private Metadata metadata;

	protected SessionFactoryScope sessionFactoryScope() {
		return sessionFactoryScope;
	}

	protected SessionFactoryImplementor sessionFactory() {
		return sessionFactoryScope.getSessionFactory();
	}

	@Override
	public SessionFactoryImplementor produceSessionFactory() {
		log.trace( "Producing SessionFactory" );

		final StandardServiceRegistryBuilder ssrBuilder = new StandardServiceRegistryBuilder()
				.applySetting( AvailableSettings.HBM2DDL_AUTO, exportSchema() ? "create-drop" : "none" );
		applySettings( ssrBuilder );
		final StandardServiceRegistry ssr = ssrBuilder.build();
		try {
			metadata = buildMetadata( ssr );
			final SessionFactoryBuilder sfBuilder = metadata.getSessionFactoryBuilder();
			configure( sfBuilder );
			final SessionFactoryImplementor factory = (SessionFactoryImplementor) metadata.buildSessionFactory();
			sessionFactoryBuilt( factory );
			return factory;
		}
		catch (Exception e) {
			StandardServiceRegistryBuilder.destroy( ssr );
			SchemaManagementToolCoordinator.ActionGrouping actions = SchemaManagementToolCoordinator.ActionGrouping.interpret(
					ssrBuilder.getSettings() );
			if ( ( exportSchema() || actions.getDatabaseAction() != Action.NONE ) && metadata != null ) {
				dropDatabase( );
			}
			throw e;
		}
	}

	private MetadataImplementor buildMetadata(StandardServiceRegistry ssr) {
		MetadataSources metadataSources = new MetadataSources( ssr );
		applyMetadataSources( metadataSources );
		return (MetadataImplementor) metadataSources.buildMetadata();
	}

	private void dropDatabase() {
		final StandardServiceRegistry ssr = new StandardServiceRegistryBuilder().build();
		try {
			final DatabaseModel databaseModel = Helper.buildDatabaseModel( buildMetadata( ssr ) );
			new SchemaExport( databaseModel, ssr ).drop( EnumSet.of( TargetType.DATABASE ) );
		}
		finally {
			StandardServiceRegistryBuilder.destroy( ssr );
		}
	}

	protected void applySettings(StandardServiceRegistryBuilder builer) {
	}

	protected void configure(SessionFactoryBuilder builder) {
	}

	protected void sessionFactoryBuilt(SessionFactoryImplementor factory) {
	}

	protected boolean strictJpaCompliance() {
		return false;
	}

	protected boolean exportSchema() {
		return true;
	}

	protected void applyMetadataSources(MetadataSources metadataSources) {
		for ( Class annotatedClass : getAnnotatedClasses() ) {
			metadataSources.addAnnotatedClass( annotatedClass );
		}
	}

	protected static final Class[] NO_CLASSES = new Class[0];

	protected Class[] getAnnotatedClasses() {
		return NO_CLASSES;
	}

	@Override
	public void injectSessionFactoryScope(SessionFactoryScope scope) {
		sessionFactoryScope = scope;
	}

	@Override
	public SessionFactoryProducer getSessionFactoryProducer() {
		return this;
	}

	protected Metadata getMetadata(){
		return  metadata;
	}

	@AfterEach
	public final void afterTest() {
		if ( isCleanupTestDataRequired() ) {
			cleanupTestData();
		}
	}

	protected boolean isCleanupTestDataRequired() {
		return false;
	}

	protected void cleanupTestData() {
		doInHibernate(this::sessionFactory, session -> {
			Arrays.stream( getAnnotatedClasses() ).forEach( annotatedClass ->
				session.createQuery( "delete from " + annotatedClass.getSimpleName() ).executeUpdate()
			);
		});
	}
}