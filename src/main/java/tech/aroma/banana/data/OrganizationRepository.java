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
import tech.aroma.banana.thrift.Organization;
import tech.aroma.banana.thrift.User;
import tech.sirwellington.alchemy.annotations.arguments.Required;


/**
 * Answers questions about and performs actions to {@linkplain Organization Organizations}.
 * 
 * @author SirWellington
 */
public interface OrganizationRepository 
{
    void saveOrganization(@Required Organization organization) throws TException;
                                                                                 
    Organization getOrganization(@Required String organizationId) throws TException;
    
    void deleteOrganization(@Required String organizationId) throws TException;
    
    boolean organizationExists(@Required String organizationId) throws TException;
    
    List<User> getOrganizationMemebers(@Required String organizationId) throws TException;
    
    List<Organization> searchByName(@Required String searchTerm) throws TException;
    
    List<User> getOrganizationOwners(@Required String organizationId) throws TException;
}
