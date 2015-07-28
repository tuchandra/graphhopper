/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class AbstractFlagEncoderTest
{
    @Test
    public void testAcceptsCar()
    {
        CarFlagEncoder encoder = new CarFlagEncoder(5, 5, 0);
        assertEquals(40, encoder.parseSpeed("40 km/h"), 1e-3);
        assertEquals(40, encoder.parseSpeed("40km/h"), 1e-3);
        assertEquals(40, encoder.parseSpeed("40kmh"), 1e-3);
        assertEquals(64.374, encoder.parseSpeed("40mph"), 1e-3);
        assertEquals(48.28, encoder.parseSpeed("30 mph"), 1e-3);
        assertEquals(-1, encoder.parseSpeed(null), 1e-3);
        assertEquals(18.52, encoder.parseSpeed("10 knots"), 1e-3);
        assertEquals(19, encoder.parseSpeed("19 kph"), 1e-3);
        assertEquals(19, encoder.parseSpeed("19kph"), 1e-3);

        assertEquals(50, encoder.parseSpeed("RO:urban"), 1e-3);

        assertEquals(80, encoder.parseSpeed("RU:rural"), 1e-3);

        assertEquals(6, encoder.parseSpeed("walk"), 1e-3);
    }

    @Test
    public void testParseDuration()
    {
        assertEquals(10, AbstractFlagEncoder.parseDuration("00:10"));
        assertEquals(70, AbstractFlagEncoder.parseDuration("01:10"));
        assertEquals(0, AbstractFlagEncoder.parseDuration("oh"));
        assertEquals(0, AbstractFlagEncoder.parseDuration(null));
        assertEquals(60 * 20, AbstractFlagEncoder.parseDuration("20:00"));
        assertEquals(60 * 20, AbstractFlagEncoder.parseDuration("0:20:00"));
        assertEquals(60 * 24 * 2 + 60 * 20 + 2, AbstractFlagEncoder.parseDuration("02:20:02"));
    }

    @Test
    public void parseConditional()
    {
        String key = "access:conditional", value = "no @ (Nov 16-Aug 14)";
        List<String[]> list = AbstractFlagEncoder.parseConditional(value);
        String[] values = list.get(0);
        assertEquals("no", values[0]);
        assertEquals("(Nov 16-Aug 14)", values[1]);

        assertNull(AbstractFlagEncoder.parseConditional("no @"));
    }

    @Test
    public void testDoesConditionHold() throws ParseException
    {
        CarFlagEncoder encoder = new CarFlagEncoder();

        // simple interval
        List<String[]> list = AbstractFlagEncoder.parseConditional("no @ (Aug 10-Aug 14)");
        String values[] = list.get(0);
        assertTrue(encoder.doesConditionHold(parse(encoder, "Aug 11"), values[1]));
        assertFalse(encoder.doesConditionHold(parse(encoder, "Aug 09"), values[1]));
        assertFalse(encoder.doesConditionHold(parse(encoder, "Aug 9"), values[1]));

        // 'inverse' interval
        list = AbstractFlagEncoder.parseConditional("no @ (Nov 16-Aug 14)");
        values = list.get(0);

        assertTrue(encoder.doesConditionHold(parse(encoder, "Aug 11"), values[1]));
        assertFalse(encoder.doesConditionHold(parse(encoder, "Aug 15"), values[1]));
        assertTrue(encoder.doesConditionHold(parse(encoder, "Nov 17"), values[1]));
        assertFalse(encoder.doesConditionHold(parse(encoder, "Nov 15"), values[1]));

        // interval without day
        list = AbstractFlagEncoder.parseConditional("destination @ (Mar-Oct)");
        values = list.get(0);

        assertTrue(encoder.doesConditionHold(parse(encoder, "Mar 1"), values[1]));
        assertTrue(encoder.doesConditionHold(parse(encoder, "Aug 11"), values[1]));
        assertTrue(encoder.doesConditionHold(parse(encoder, "Oct 30"), values[1]));
        assertFalse(encoder.doesConditionHold(parse(encoder, "Feb 28"), values[1]));
        assertFalse(encoder.doesConditionHold(parse(encoder, "Nov 11"), values[1]));

        // 'inverse' interval without day
        list = AbstractFlagEncoder.parseConditional("destination @ (Oct-Mar)");
        values = list.get(0);

        assertTrue(encoder.doesConditionHold(parse(encoder, "Mar 30"), values[1]));
        assertFalse(encoder.doesConditionHold(parse(encoder, "Apr 1"), values[1]));
        assertTrue(encoder.doesConditionHold(parse(encoder, "Oct 1"), values[1]));
        assertFalse(encoder.doesConditionHold(parse(encoder, "Sep 30"), values[1]));

        // access:conditional=destination @ summer
        // access:conditional=no @ (Nov 16-Aug 14)
        // access:conditional=yes @ (Jul 15-Nov 15)
        // access:conditional=no @ (Tu,Th)
        // access:conditional=no @ (22:00-06:00)
        // access:conditional=no @ Fr
        // access:conditional=no @ (Mo-Su 00:00-06:00)
        // access:conditional=no @ (Mo-Fr 09:20-09:35, 11:10-11:20); yes @ SH
        // access:conditional=yes @ 01/01 to 02/29;07/01 to 12/31
        //                    description:de=(gesperrt 1.3.-30.6.)
        // access:conditional=no @ (Oct 10-Mar 31; Apr 01-Oct 09 19:00-07:00)
        // access:conditional=destination @ (Mar-Oct)
        // bicycle:conditional=yes @ (Mar-Oct)
    }

    Date parse( AbstractFlagEncoder encoder, String str ) throws ParseException
    {
        return encoder.parse(encoder.monthDateFormatter, str).getTime();
    }
}
