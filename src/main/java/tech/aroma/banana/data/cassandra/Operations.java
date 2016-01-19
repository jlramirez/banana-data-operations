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


import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.sirwellington.alchemy.annotations.access.Internal;
import tech.sirwellington.alchemy.annotations.access.NonInstantiable;
import tech.sirwellington.alchemy.annotations.arguments.Required;

import static tech.sirwellington.alchemy.arguments.Arguments.checkThat;
import static tech.sirwellington.alchemy.arguments.assertions.Assertions.notNull;

/**
 *
 * @author SirWellington
 */
@Internal
@NonInstantiable
public final class Operations 
{
    private final static Logger LOG = LoggerFactory.getLogger(Operations.class);

    
    /**
     * Measures the time it takes to run an operation, in ms.
     * 
     * @param operation The operation to call
     * 
     * @return The Time it took for the operation to complete, in ms.
     * @throws Exception 
     */
    public static long measureOperation(@Required Callable<?> operation) throws Exception
    {
        checkThat(operation).is(notNull());
        
        long start = System.currentTimeMillis();
        
        operation.call();
        
        long end = System.currentTimeMillis();
        
        return end - start;
    }
    
    public static <T> T logLatency(@Required Callable<T> operation, String operationName) throws Exception
    {
        checkThat(operation).is(notNull());
        
        long start = System.currentTimeMillis();
        
        try
        {
            return operation.call();
        }
        finally
        {
            long end = System.currentTimeMillis();
            LOG.debug("{} Operation took {} ms", operationName, end - start);
        }
    }
}