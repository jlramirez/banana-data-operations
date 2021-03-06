/*
 * Copyright 2016 Aroma Tech.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.aroma.banana.data.cassandra;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sir.wellington.alchemy.collections.lists.Lists;
import sir.wellington.alchemy.collections.sets.Sets;
import tech.aroma.banana.data.ApplicationRepository;
import tech.aroma.banana.thrift.Application;
import tech.aroma.banana.thrift.ProgrammingLanguage;
import tech.aroma.banana.thrift.Tier;
import tech.aroma.banana.thrift.exceptions.ApplicationDoesNotExistException;
import tech.aroma.banana.thrift.exceptions.InvalidArgumentException;
import tech.aroma.banana.thrift.exceptions.OperationFailedException;

import static com.datastax.driver.core.querybuilder.QueryBuilder.contains;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.ttl;
import static tech.aroma.banana.data.assertions.RequestAssertions.isNullOrEmpty;
import static tech.aroma.banana.data.assertions.RequestAssertions.validApplication;
import static tech.aroma.banana.data.cassandra.Tables.ApplicationsTable.APP_DESCRIPTION;
import static tech.aroma.banana.data.cassandra.Tables.ApplicationsTable.APP_ID;
import static tech.aroma.banana.data.cassandra.Tables.ApplicationsTable.APP_NAME;
import static tech.aroma.banana.data.cassandra.Tables.ApplicationsTable.ORG_ID;
import static tech.aroma.banana.data.cassandra.Tables.ApplicationsTable.OWNERS;
import static tech.aroma.banana.data.cassandra.Tables.ApplicationsTable.PROGRAMMING_LANGUAGE;
import static tech.aroma.banana.data.cassandra.Tables.ApplicationsTable.TABLE_NAME;
import static tech.aroma.banana.data.cassandra.Tables.ApplicationsTable.TABLE_NAME_RECENTLY_CREATED;
import static tech.aroma.banana.data.cassandra.Tables.ApplicationsTable.TIER;
import static tech.aroma.banana.data.cassandra.Tables.ApplicationsTable.TIME_PROVISIONED;
import static tech.sirwellington.alchemy.arguments.Arguments.checkThat;
import static tech.sirwellington.alchemy.arguments.assertions.Assertions.notNull;
import static tech.sirwellington.alchemy.arguments.assertions.StringAssertions.nonEmptyString;
import static tech.sirwellington.alchemy.arguments.assertions.StringAssertions.stringWithLengthGreaterThanOrEqualTo;
import static tech.sirwellington.alchemy.arguments.assertions.StringAssertions.validUUID;

/**
 *
 * @author SirWellington
 */
final class CassandraApplicationRepository implements ApplicationRepository
{
    
    private final static Logger LOG = LoggerFactory.getLogger(CassandraApplicationRepository.class);
    private final static Duration DEFAULT_RECENT_DURATION = Duration.ofDays(3);
    
    private final Session cassandra;
    private final QueryBuilder queryBuilder;
    
    @Inject
    CassandraApplicationRepository(Session cassandra, QueryBuilder queryBuilder)
    {
        checkThat(queryBuilder, cassandra)
            .are(notNull());
        
        this.cassandra = cassandra;
        this.queryBuilder = queryBuilder;
    }
    
    @Override
    public void saveApplication(Application application) throws TException
    {
        checkThat(application)
            .throwing(InvalidArgumentException.class)
            .is(validApplication());
        
        Statement statement = createInsertStatementFor(application);
        
        try
        {
            cassandra.execute(statement);
            LOG.debug("Successfully saved Application in Cassandra: {}", application);
        }
        catch (Exception ex)
        {
            LOG.error("Failed to store Application in Cassandra: {}", application, ex);
            throw new OperationFailedException("Could not save Application: " + ex.getMessage());
        }
    }
    
    @Override
    public void deleteApplication(String applicationId) throws TException
    {
        checkApplicationId(applicationId);

        //Must fetch the full Application first
        Application app = this.getById(applicationId);
        
        Statement statement = createDeleteStatementFor(app);
        
        try
        {
            cassandra.execute(statement);
            LOG.debug("Successfully deleted Application with ID {}", applicationId);
        }
        catch (Exception ex)
        {
            LOG.error("Failed to delete application with ID [{}] from Cassandra", applicationId, ex);
            throw new OperationFailedException("Could not delete Application with ID: " + applicationId);
        }
    }
    
    @Override
    public Application getById(String applicationId) throws TException
    {
        checkApplicationId(applicationId);
        
        Statement query = createQueryForAppWithId(applicationId);
        
        ResultSet results;
        
        try
        {
            results = cassandra.execute(query);
        }
        catch (Exception ex)
        {
            LOG.error("Failed to query for application with ID {}", applicationId, ex);
            throw new OperationFailedException("Could not Query Application with ID: " + applicationId);
        }
        
        Row row = results.one();
        checkRowNotMissing(applicationId, row);
        
        Application app = createApplicationFromRow(row);
        
        return app;
    }
    
    @Override
    public boolean containsApplication(String applicationId) throws TException
    {
        checkApplicationId(applicationId);
        
        Statement query = createQueryToCheckIfAppIdExists(applicationId);
        
        ResultSet results;
        
        try
        {
            results = cassandra.execute(query);
        }
        catch (Exception ex)
        {
            LOG.error("Failed to check Application existence for [{}]", applicationId, ex);
            throw new OperationFailedException("Could not check for application existence: " + applicationId);
        }
        
        Row row = results.one();
        checkRowNotMissing(applicationId, row);
        
        long count = row.getLong(0);
        return count > 0L;
    }
    
    @Override
    public List<Application> getApplicationsOwnedBy(String userId) throws TException
    {
        checkThat(userId)
            .throwing(InvalidArgumentException.class)
            .usingMessage("missing userId")
            .is(nonEmptyString())
            .usingMessage("invalid UUID Type")
            .is(validUUID());
        
        Statement query = createQueryForAppsOwnedBy(userId);
        
        ResultSet results;
        
        try
        {
            results = cassandra.execute(query);
        }
        catch (Exception ex)
        {
            LOG.error("Failed to query for Apps owned by {}", userId, ex);
            throw new OperationFailedException("Could not determine Apps owned by user: " + userId);
        }
        
        List<Application> apps = Lists.create();
        
        for (Row row : results)
        {
            if (row == null)
            {
                continue;
            }
            
            Application app = createApplicationFromRow(row);
            apps.add(app);
        }
        
        LOG.debug("Found {} apps owned by user {}", apps.size(), userId);
        
        return apps;
    }
    
    @Override
    public List<Application> getApplicationsByOrg(String orgId) throws TException
    {
        checkThat(orgId)
            .throwing(InvalidArgumentException.class)
            .usingMessage("missing orgId")
            .is(nonEmptyString())
            .usingMessage("invalid UUID type for org")
            .is(validUUID());
        
        Statement query = createQueryForAppsWithOrg(orgId);
        
        ResultSet results;
        
        try
        {
            results = cassandra.execute(query);
        }
        catch (Exception ex)
        {
            LOG.error("Failed to find Apps by Org with ID [{}]", orgId, ex);
            throw new OperationFailedException("Could not find Org's Apps: " + orgId);
        }
        
        List<Application> apps = Lists.create();
        
        for (Row row : results)
        {
            Application app = createApplicationFromRow(row);
            apps.add(app);
        }
        
        LOG.debug("Found {} apps in Org {}", apps.size(), orgId);
        
        return apps;
        
    }
    
    @Override
    public List<Application> searchByName(String searchTerm) throws TException
    {
        checkSearchTerm(searchTerm);
        
        throw new OperationFailedException("Searching not supported yet");
    }
    
    @Override
    public List<Application> getRecentlyCreated() throws TException
    {
        List<Application> apps = Lists.create();
        
        Statement query = createQueryForRecentlyCreatedApps();
        
        ResultSet results = null;
        
        try
        {
            results = cassandra.execute(query);
        }
        catch (Exception ex)
        {
            LOG.error("Failed to query for recently created apps", ex);
        }
        
        if (results != null)
        {
            for (Row row : results)
            {
                Application app = createApplicationFromRow(row);
                apps.add(app);
            }
        }
        
        LOG.debug("Found {} recently created apps", apps.size());
        
        return apps;
    }
    
    private Statement createInsertStatementFor(Application app)
    {
        BatchStatement batch = new BatchStatement();
        
        UUID appId = UUID.fromString(app.applicationId);
        UUID orgId = UUID.fromString(app.organizationId);
        Set<UUID> owners = Sets.nullToEmpty(app.owners)
            .stream()
            .map(UUID::fromString)
            .collect(Collectors.toSet());
        
        Statement insertIntoMainTable = queryBuilder
            .insertInto(TABLE_NAME)
            .value(APP_ID, appId)
            .value(APP_NAME, app.name)
            .value(APP_DESCRIPTION, app.applicationDescription)
            .value(ORG_ID, orgId)
            .value(OWNERS, owners)
            .value(PROGRAMMING_LANGUAGE, app.programmingLanguage)
            .value(TIME_PROVISIONED, app.timeOfProvisioning)
            .value(TIER, app.tier);
        
        batch.add(insertIntoMainTable);
        
        Long timeToLive = DEFAULT_RECENT_DURATION.getSeconds();
        
        Statement insertIntoRecentlyCreated = queryBuilder
            .insertInto(TABLE_NAME_RECENTLY_CREATED)
            .value(APP_ID, appId)
            .value(APP_NAME, app.name)
            .value(APP_DESCRIPTION, app.applicationDescription)
            .value(ORG_ID, orgId)
            .value(OWNERS, owners)
            .value(PROGRAMMING_LANGUAGE, app.programmingLanguage)
            .value(TIME_PROVISIONED, app.timeOfProvisioning)
            .value(TIER, app.tier)
            .using(ttl(timeToLive.intValue()));
        
        batch.add(insertIntoRecentlyCreated);
        return batch;
    }
    
    private Statement createDeleteStatementFor(Application app)
    {
        BatchStatement batch = new BatchStatement();
        
        UUID appId = UUID.fromString(app.applicationId);
        
        Statement deleteFromMainTable = queryBuilder
            .delete()
            .all()
            .from(TABLE_NAME)
            .where(eq(APP_ID, appId));
        
        batch.add(deleteFromMainTable);
        
        Statement deleteFromRecentsTable = queryBuilder
            .delete()
            .all()
            .from(TABLE_NAME_RECENTLY_CREATED)
            .where(eq(APP_ID, appId));
        batch.add(deleteFromRecentsTable);
        
        return batch;
    }
    
    private Statement createQueryForAppWithId(String applicationId)
    {
        UUID appId = UUID.fromString(applicationId);
        
        return queryBuilder
            .select()
            .all()
            .from(TABLE_NAME)
            .where(eq(APP_ID, appId))
            .limit(2);
    }
    
    private Application createApplicationFromRow(Row row) throws OperationFailedException
    {
        Application app = new Application();
        
        UUID appId = row.getUUID(APP_ID);
        checkThat(appId)
            .throwing(OperationFailedException.class)
            .usingMessage("missing appId")
            .is(notNull());
        
        app.setApplicationId(appId.toString());
        
        String programmingLanguage = row.getString(PROGRAMMING_LANGUAGE);
        if (!isNullOrEmpty(programmingLanguage))
        {
            ProgrammingLanguage language = ProgrammingLanguage.valueOf(programmingLanguage);
            app.setProgrammingLanguage(language);
        }
        
        Date timeOfProvisioning = row.getTimestamp(TIME_PROVISIONED);
        if (timeOfProvisioning != null)
        {
            app.setTimeOfProvisioning(timeOfProvisioning.getTime());
        }
        
        //Transform the UUIDs to Strings
        Set<String> owners = row.getSet(OWNERS, UUID.class)
            .stream()
            .map(UUID::toString)
            .collect(Collectors.toSet());
        
        app.setOwners(owners);
        
        UUID orgId = row.getUUID(ORG_ID);
        if (orgId != null)
        {
            app.setOrganizationId(orgId.toString());
        }
        
        String tier = row.getString(TIER);
        if (!isNullOrEmpty(tier))
        {
            app.setTier(Tier.valueOf(tier));
        }
        
        app.setName(row.getString(APP_NAME))
            .setApplicationDescription(row.getString(APP_DESCRIPTION));
        
        return app;
    }
    
    private void checkApplicationId(String applicationId) throws InvalidArgumentException
    {
        checkThat(applicationId)
            .throwing(InvalidArgumentException.class)
            .usingMessage("missing applicationId")
            .is(nonEmptyString())
            .usingMessage("invalid UUID type")
            .is(validUUID());
    }
    
    private Statement createQueryToCheckIfAppIdExists(String applicationId)
    {
        UUID appId = UUID.fromString(applicationId);
        
        return queryBuilder
            .select()
            .countAll()
            .from(TABLE_NAME)
            .where(eq(APP_ID, appId));
    }
    
    private void checkRowNotMissing(String applicationId, Row row) throws ApplicationDoesNotExistException
    {
        checkThat(row)
            .throwing(ApplicationDoesNotExistException.class)
            .usingMessage("No App with ID: " + applicationId)
            .is(notNull());
    }
    
    private Statement createQueryForAppsOwnedBy(String userId)
    {
        UUID ownerId = UUID.fromString(userId);
        
        return queryBuilder
            .select()
            .all()
            .from(TABLE_NAME)
            .where(contains(OWNERS, ownerId));
    }
    
    private Statement createQueryForAppsWithOrg(String orgId)
    {
        UUID uuid = UUID.fromString(orgId);
        
        return queryBuilder
            .select()
            .all()
            .from(TABLE_NAME)
            .where(eq(ORG_ID, uuid));
    }
    
    private void checkSearchTerm(String searchTerm) throws InvalidArgumentException
    {
        checkThat(searchTerm)
            .throwing(InvalidArgumentException.class)
            .is(stringWithLengthGreaterThanOrEqualTo(2))
            .is(nonEmptyString());
    }
    
    private Statement createQueryForRecentlyCreatedApps()
    {
        return queryBuilder
            .select()
            .all()
            .from(TABLE_NAME_RECENTLY_CREATED)
            .limit(200)
            .allowFiltering();
    }
    
}
