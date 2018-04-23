/**********************************************************************
 * Copyright (c) 2016 Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/
package org.eclipse.tracecompass.lttng2.ust.core.analysis.java;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.lttng2.ust.core.trace.LttngUstTrace;
import org.eclipse.tracecompass.lttng2.ust.core.trace.layout.ILttngUstEventLayout;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;

/**
 * @author Houssem Daoud
 */
public class JavaStateProvider extends AbstractTmfStateProvider {

    /* Version of this state provider */
    private static final int VERSION = 1;
    private static final Long MINUS_ONE = Long.valueOf(-1);
    private final @NonNull ILttngUstEventLayout fLayout;


    /**
     * Constructor
     *
     * @param trace
     *            trace
     */
    public JavaStateProvider(@NonNull LttngUstTrace trace) {
        super(trace, "Ust:Java"); //$NON-NLS-1$
        fLayout = trace.getEventLayout();
    }

    private Long getVtid(ITmfEvent event) {
        /* We checked earlier that the "vtid" context is present */
        ITmfEventField field = event.getContent().getField(fLayout.contextVtid());
        if (field == null) {
            return MINUS_ONE;
        }
        return (Long)field.getValue();
    }

    @Override
    protected void eventHandle(ITmfEvent event) {
        String name = event.getName();
        ITmfStateSystemBuilder ss = checkNotNull(getStateSystemBuilder());
        long ts = event.getTimestamp().toNanos();
        Long tid = getVtid(event);


        switch (name) {
        case "jvm:thread_start": { //$NON-NLS-1$
           int tidQuark = ss.getQuarkAbsoluteAndAdd(tid.toString());
           int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
           String threadname  = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
           int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
           ss.modifyAttribute(ts, TmfStateValue.newValueString(threadname), nameQuark);
           ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "jvm:thread_status": { //$NON-NLS-1$
            int tidQuark = ss.getQuarkAbsoluteAndAdd(tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            Long status = (Long)event.getContent().getField("status").getValue(); //$NON-NLS-1$
            if (status==2) {
                ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
            } else {
                ss.modifyAttribute(ts, TmfStateValue.newValueLong(status), statusQuark);
            }
        }
            break;
        default:
            /* Ignore other event types */
            break;
        }

    }

    @Override
    public ITmfStateProvider getNewInstance() {
        return new JavaStateProvider(getTrace());
    }

    @Override
    public LttngUstTrace getTrace() {
        return (LttngUstTrace) super.getTrace();
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

}
