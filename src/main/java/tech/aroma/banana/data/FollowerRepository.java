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


package tech.aroma.banana.data;

import java.util.List;
import org.apache.thrift.TException;
import tech.aroma.banana.thrift.Application;
import tech.aroma.banana.thrift.User;
import tech.sirwellington.alchemy.annotations.arguments.Required;


/**
 * Contains operations related to the following of Applications by Users.
 * ß
 * @author SirWellington
 */
public interface FollowerRepository 
{
    void saveFollowing(@Required User user, @Required Application application) throws TException;
    
    void deleteFollowing(@Required String userId, @Required String applicationId) throws TException;
    
    boolean followingExists(@Required String userId, @Required String applicationId) throws TException;
    
    List<Application> getApplicationsFollowedBy(@Required String userId) throws TException;
    
    List<User> getApplicationFollowers(@Required String applicationId) throws TException;
}
