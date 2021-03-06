package org.jacorb.test.notification.perf;

/*
 *        JacORB - a free Java ORB
 *
 *   Copyright (C) 1999-2012 Gerald Brose / The JacORB Team.
 *
 *   This library is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Library General Public
 *   License as published by the Free Software Foundation; either
 *   version 2 of the License, or (at your option) any later version.
 *
 *   This library is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *   Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this library; if not, write to the Free
 *   Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jacorb.notification.util.LogUtil;
import org.jacorb.test.common.TestUtils;
import org.jacorb.test.notification.NotificationTestUtils;
import org.jacorb.test.notification.StructuredPushReceiver;
import org.jacorb.test.notification.StructuredPushSender;
import org.jacorb.test.notification.common.NotifyServerTestCase;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.omg.CORBA.Any;
import org.omg.CORBA.IntHolder;
import org.omg.CosEventComm.Disconnected;
import org.omg.CosNotification.EventType;
import org.omg.CosNotification.Property;
import org.omg.CosNotification.StructuredEvent;
import org.omg.CosNotifyChannelAdmin.ConsumerAdmin;
import org.omg.CosNotifyChannelAdmin.EventChannel;
import org.omg.CosNotifyChannelAdmin.EventChannelFactory;
import org.omg.CosNotifyChannelAdmin.SupplierAdmin;
import org.omg.CosNotifyFilter.ConstraintExp;
import org.omg.CosNotifyFilter.Filter;
import org.omg.CosNotifyFilter.FilterFactory;
import org.omg.CosNotifyFilter.InvalidConstraint;
import org.omg.CosNotifyFilter.InvalidGrammar;
import org.slf4j.Logger;


/**
 * @author Alphonse Bendt
 */

public class PerformanceTest extends NotifyServerTestCase
{
    @BeforeClass
    public static void beforeClassSetUp() throws Exception
    {
        Assume.assumeTrue(System.getProperty("jacorb.test.notificationperf", "false").equals("true"));
    }

    EventChannelFactory factory_;

    FilterFactory filterFactory_;

    Any testPerson_;

    EventChannel channel_;

    IntHolder channelId_;

    SupplierAdmin supplierAdmin_;

    ConsumerAdmin consumerAdmin_;

    Filter trueFilter_;

    NotificationTestUtils testUtils_;

    Logger logger_ = LogUtil.getLogger(getClass().getName());

    static long sInterval = 250L;

    static long sTimeout = 2 * sInterval;

    /**
     * Creates a new <code>PerformanceTest</code> instance.
     *
     * @param name
     *            test name
     */
    /**
     * setup EventChannelFactory, FilterFactory and Any with Testdata
     */
    @Before
    public void setUp() throws Exception
    {
        testUtils_ = new NotificationTestUtils();

        factory_ = getEventChannelFactory();

        // prepare test data
        testPerson_ = testUtils_.getTestPersonAny();

        // setup a channel
        channelId_ = new IntHolder();

        channel_ = factory_.create_channel(new Property[0], new Property[0], channelId_);

        filterFactory_ = channel_.default_filter_factory();

        supplierAdmin_ = channel_.default_supplier_admin();

        consumerAdmin_ = channel_.default_consumer_admin();

        trueFilter_ = filterFactory_.create_filter("EXTENDED_TCL");
        ConstraintExp[] _constraintExp = new ConstraintExp[1];
        EventType[] _eventType = new EventType[1];
        _eventType[0] = new EventType("*", "*");
        String _expression = "TRUE";
        _constraintExp[0] = new ConstraintExp(_eventType, _expression);
        trueFilter_.add_constraints(_constraintExp);
    }

    @After
    public void tearDownTest() throws Exception
    {
        trueFilter_.destroy();

        try
        {
            channel_.destroy();
        } catch (Exception e)
        {
            // ignore
        }
    }

    @Test
    public void testCompareAny() throws Exception
    {
        Any _a1 = setup.getClientOrb().create_any(), _a2 = setup.getClientOrb().create_any();

        _a1.insert_long(10);
        _a2.insert_long(10);

        assertEquals(_a1, _a2);
    }

    @Test
    public void testMeasureFilterLatency() throws Exception
    {
        Any _any = setup.getClientOrb().create_any();
        _any.insert_long(10);

        int _runs = 100;

        measureFilterLatency("$ == 10", _any, _runs);

        _any = testUtils_.getTestPersonAny();

        measureFilterLatency("$.phone_numbers[0] == '12345678'", _any, _runs);

        measureFilterLatency("exist $.phone_numbers[0] and $.phone_numbers[0] == '12345678'", _any,
                _runs);

        measureFilterLatency(
                "exist $.phone_numbers[0] and exist $.phone_numbers[0]  and $.phone_numbers[0] == '12345678'",
                _any, _runs);

        measureFilterLatency(
                "exist $.phone_numbers[0] and exist $.phone_numbers[0] and exist $.phone_numbers[0] and $.phone_numbers[0] == '12345678'",
                _any, _runs);

        measureFilterLatency(
                "exist $.phone_numbers[0] and exist $.phone_numbers[0] and exist $.phone_numbers[0] and exist $.phone_numbers[0] and $.phone_numbers[0] == '12345678'",
                _any, _runs);

        measureFilterLatency(
                "exist $.phone_numbers[0] and exist $.phone_numbers[0] and exist $.phone_numbers[0] and exist $.phone_numbers[0] and exist $.phone_numbers[0] and $.phone_numbers[0] == '12345678'",
                _any, _runs);

        StructuredEvent _event = testUtils_.getStructuredEvent();

        measureFilterLatency("$event_name == 'ALARM'", _event, _runs);

        measureFilterLatency("$type_name == 'TESTING'", _event, _runs);

        measureFilterLatency("$domain_name == 'TESTING'", _event, _runs);

        Thread.sleep(120000);
    }

    private void measureFilterLatency(String filterString, StructuredEvent event, int runs)
            throws Exception
    {
        Filter _filter = createFilter(filterString);

        long _start = System.currentTimeMillis();

        for (int x = 0; x < runs; ++x)
        {
            boolean _r = _filter.match_structured(event);
            assertTrue(_r);
        }

        long _total = System.currentTimeMillis() - _start;

        TestUtils.getLogger().debug(runs + " Filterings of '" + filterString + "' took " + _total
                + " in average: " + (_total / runs));
    }

    private Filter createFilter(String filterString) throws InvalidGrammar, InvalidConstraint
    {
        Filter _filter = filterFactory_.create_filter("EXTENDED_TCL");
        ConstraintExp[] _constraintExp = new ConstraintExp[1];
        EventType[] _eventType = new EventType[1];
        _eventType[0] = new EventType("*", "*");
        String _expression = filterString;
        _constraintExp[0] = new ConstraintExp(_eventType, _expression);
        _filter.add_constraints(_constraintExp);

        return _filter;
    }

    private void measureFilterLatency(String filterString, Any event, int runs) throws Exception
    {
        Filter _filter = createFilter(filterString);

        long _start = System.currentTimeMillis();

        for (int x = 0; x < runs; ++x)
        {
            boolean _r = _filter.match(event);
            assertTrue(_r);
        }

        long _total = System.currentTimeMillis() - _start;

        TestUtils.getLogger().debug(runs + " Filterings of '" + filterString + "' took " + _total
                + " in average: " + (_total / runs));
    }

    @Test
    public void testLoad() throws Exception
    {
        final AtomicBoolean active = new AtomicBoolean(true);

        final StructuredPushReceiver receiver = new StructuredPushReceiver(setup.getClientOrb());

        receiver.connect(channel_, false);

        final StructuredPushSender sender = new StructuredPushSender(setup.getClientOrb());

        sender.connect(channel_, false);

        while (active.get())
        {
            final StructuredEvent[] data = new StructuredEvent[1000];

            for (int x = 0; x < data.length; ++x)
            {
                data[x] = testUtils_.getStructuredEvent();
            }

            final CountDownLatch latch = new CountDownLatch(1);
            new Thread()
            {
                @Override
                public void run()
                {
                    TestUtils.getLogger().debug("Begin to send");

                    try
                    {
                        sender.pushEvents(data);
                    } catch (Disconnected e)
                    {
                        active.set(false);
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    TestUtils.getLogger().debug("Sent " + data.length);

                    latch.countDown();
                }
            }.start();

            latch.await();

            TestUtils.getLogger().debug(receiver.toString());

            Thread.sleep(60000);

            TestUtils.getLogger().debug(receiver.toString());
        }

        sender.shutdown();

        receiver.shutdown();
    }
}
