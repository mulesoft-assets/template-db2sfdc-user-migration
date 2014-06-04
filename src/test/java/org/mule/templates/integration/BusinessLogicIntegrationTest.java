package org.mule.templates.integration;

import java.util.Date;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.construct.Flow;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.templates.builders.ObjectBuilder;
import org.mule.templates.db.MySQLDbCreator;

import com.mulesoft.module.batch.BatchTestHelper;

/**
 * The objective of this class is to validate the correct behavior of the flows
 * for this Mule Template that make calls to external systems.
 */
public class BusinessLogicIntegrationTest extends AbstractTemplateTestCase {

	private static Logger log = Logger.getLogger(BusinessLogicIntegrationTest.class);

	private static final String PATH_TO_TEST_PROPERTIES = "./src/test/resources/mule.test.properties";
	private static final String PATH_TO_SQL_SCRIPT = "src/main/resources/user.sql";
	private static final String DATABASE_NAME = "DB2SFDCUserMigration" + new Long(new Date().getTime()).toString();
	private static final MySQLDbCreator DBCREATOR = new MySQLDbCreator(DATABASE_NAME, PATH_TO_SQL_SCRIPT, PATH_TO_TEST_PROPERTIES);

	Map<String, Object> user = null;
	private BatchTestHelper helper;

	@Rule
	public DynamicPort port = new DynamicPort("http.port");
	
	@BeforeClass
	public static void init() {
		System.setProperty("database.url", DBCREATOR.getDatabaseUrlWithName());
		DBCREATOR.setUpDatabase();
	}


	@Before
	public void setUp() throws Exception {
		helper = new BatchTestHelper(muleContext);
		createUsersInDB();
	}

	@After
	public void tearDown() throws Exception {
		//deleteUserFromDB();
		DBCREATOR.tearDownDataBase();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testMainFlow() throws Exception {
		runFlow("mainFlow");

		helper.awaitJobTermination(120 * 1000, 500);
		helper.assertJobWasSuccessful();

		SubflowInterceptingChainLifecycleWrapper subflow = getSubFlow("querySalesforce");
		subflow.initialise();

		MuleEvent event = subflow.process(getTestEvent(user, MessageExchangePattern.REQUEST_RESPONSE));
		Map<String, Object> result = (Map<String, Object>) event.getMessage().getPayload();
		log.info("querySalesforce result: " + result);

		Assert.assertNotNull(result);
		Assert.assertEquals("There should be matching user in Salesforce now", user.get("email"), result.get("Email"));
	}

	private void createUsersInDB() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("insertUserDB");
		flow.initialise();
		user = createDbUser();

		MuleEvent event = flow.process(getTestEvent(user, MessageExchangePattern.REQUEST_RESPONSE));
		Object result = event.getMessage().getPayload();
		log.info("insertUserDB result: " + result);
	}

	private void deleteUserFromDB() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("deleteUserDB");
		flow.initialise();

		MuleEvent event = flow.process(getTestEvent(user, MessageExchangePattern.REQUEST_RESPONSE));
		Object result = event.getMessage().getPayload();
		log.info("deleteUserDB result: " + result);
	}

	private Map<String, Object> createDbUser() {
		String name = "tst" + new Long(new Date().getTime()).toString();
		return ObjectBuilder.aUser()
				.with("firstname", name)
				.with("lastname", name)
				// set email to existin sf user to prevent creating new one
				.with("email","tstlrssi.1401865118651@fakemailuser-broadcast.com")
				.build();
	}
}
