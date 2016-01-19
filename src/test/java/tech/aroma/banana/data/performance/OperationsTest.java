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

package tech.aroma.banana.data.performance;

import java.util.concurrent.Callable;
import org.apache.thrift.TException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import tech.sirwellington.alchemy.test.junit.runners.AlchemyTestRunner;
import tech.sirwellington.alchemy.test.junit.runners.GenerateInteger;
import tech.sirwellington.alchemy.test.junit.runners.GenerateString;
import tech.sirwellington.alchemy.test.junit.runners.Repeat;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.sirwellington.alchemy.generator.AlchemyGenerator.one;
import static tech.sirwellington.alchemy.generator.NumberGenerators.longs;
import static tech.sirwellington.alchemy.test.junit.ThrowableAssertion.assertThrows;

/**
 *
 * @author SirWellington
 */
@Repeat(5)
@RunWith(AlchemyTestRunner.class)
public class OperationsTest 
{

    @GenerateInteger
    private int value;
    
    @GenerateString
    private String operationName;
    
    @Before
    public void setUp()
    {
    }

    @Test
    public void testMeasureOperation() throws Exception
    {
        long sleepTimeMillis = one(longs(5, 50));
        
        Callable operation = () -> 
        {
           Thread.sleep(sleepTimeMillis);
           
            System.out.println("Operation complete");
            return null;
        };
        
        long result = Operations.measureOperation(operation);
        assertThat(result, greaterThanOrEqualTo(sleepTimeMillis));
    }
    
    @Test
    public void testMeasureOperationWhenOperationThrows() throws Exception
    {
        Callable operation = () ->
        {
           throw new TException();
        };
        
        assertThrows(() -> Operations.measureOperation(operation))
            .isInstanceOf(TException.class);
        
    }

    @Test
    public void testLogLatency() throws Exception
    {
        Callable<Integer> operation = mock(Callable.class);
        when(operation.call()).thenReturn(value);
        
        Integer result = Operations.logLatency(operation, operationName);
        assertThat(result, is(value));
    }

}