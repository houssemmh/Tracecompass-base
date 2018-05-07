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
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Houssem Daoud
 */
public class JavaStateProvider extends AbstractTmfStateProvider {

    /* Version of this state provider */
    private static final int VERSION = 1;
    private static final Long MINUS_ONE = Long.valueOf(-1);
    private final @NonNull IKernelAnalysisEventLayout fLayout;
    private ArrayList<Long> stopped_threads= new ArrayList<>();
    private HashMap<Long, String> threads= new HashMap<>();


    /**
     * Constructor
     *
     * @param trace
     *            trace
     */
    public JavaStateProvider(@NonNull ITmfTrace trace, IKernelAnalysisEventLayout layout) {
        super(trace, "Ust:Java"); //$NON-NLS-1$
        fLayout = layout;
    }

    private static Long getVtid(ITmfEvent event) {
        /* We checked earlier that the "vtid" context is present */
        ITmfEventField field = event.getContent().getField("context._vtid");
        if (field == null) {
            return MINUS_ONE;
        }
        return (Long)field.getValue();
    }

//    private static String substringAfterLast(String str, String separator) {
//        int pos = str.lastIndexOf(separator);
//        if (pos == -1 || pos == (str.length() - separator.length())) {
//            return "";
//        }
//        return str.substring(pos + separator.length());
//    }

    @Override
    protected void eventHandle(ITmfEvent event) {
        String name = event.getName();
        ITmfStateSystemBuilder ss = checkNotNull(getStateSystemBuilder());
        long ts = event.getTimestamp().toNanos();

        switch (name) {
        case "jvm:thread_start": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
           int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
           Long iscompiler  = (Long)event.getContent().getField("compiler").getValue(); //$NON-NLS-1$
           int threadsQuark;
           if (iscompiler == 0) {
               threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"JavaThreads");
           } else {
               threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"CompilerThreads");
           }
           int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
           int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
           String threadname  = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
           int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
           ss.modifyAttribute(ts, TmfStateValue.newValueString(threadname), nameQuark);
           ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
           threads.put(tid, threadname);
        }
            break;
        case "jvm:thread_status": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            if (stopped_threads.contains(tid)) {
                break;
            }
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"JavaThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            Long status = (Long)event.getContent().getField("status").getValue(); //$NON-NLS-1$
            if (status==2) {
                ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
                stopped_threads.add(tid);
            } else {
                ss.modifyAttribute(ts, TmfStateValue.newValueLong(status), statusQuark);
            }
        }
            break;
        case "jvm:vmthread_start": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"VMThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            String threadname  = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(threadname), nameQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
            threads.put(tid, threadname);
         }
            break;
        case "jvm:vmthread_stop": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            String targettid  = event.getContent().getField("os_threadid").getValue().toString();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"VMThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, targettid);
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), nameQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
         }
            break;
        case "jvm:vmops_begin": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"VMThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            String operationName  = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(operationName), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(1), statusQuark);
         }
             break;
        case "jvm:vmops_end": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"VMThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
         }
            break;

        case "jvm:gctaskthread_start": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            String targettid  = event.getContent().getField("os_threadid").getValue().toString();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"GCThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(gcthreadsQuark, targettid);
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            String threadname  = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(threadname), nameQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
         }
            break;
        case "jvm:gctask_start": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"GCThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(gcthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            String operationName  = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(operationName), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(2), statusQuark);
         }
             break;
        case "jvm:gctask_end": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"GCThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(gcthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
         }
            break;
        case "jvm:contended_enter": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            String monitorName = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            Long ptr  = (Long)event.getContent().getField("ptr").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int contendedQuark = ss.getQuarkRelativeAndAdd(pidQuark,"Contention");
            int monitorQuark = ss.getQuarkRelativeAndAdd(contendedQuark, ptr.toString());
            int nameQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(monitorName), nameQuark);
            int threadsQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "threads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            String threadName = threads.get(tid);
            int threadNameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(4), statusQuark);
            if(threadName != null) {
                ss.modifyAttribute(ts, TmfStateValue.newValueString(threadName), threadNameQuark);
            } else {
                ss.modifyAttribute(ts, TmfStateValue.newValueString(tid.toString()), threadNameQuark);
            }
        }
            break;
        case "jvm:contended_entered": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            Long ptr  = (Long)event.getContent().getField("ptr").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int contendedQuark = ss.getQuarkRelativeAndAdd(pidQuark,"Contention");
            int monitorQuark = ss.getQuarkRelativeAndAdd(contendedQuark, ptr.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "threads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "jvm:monitor_wait": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            String monitorName = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            Long ptr  = (Long)event.getContent().getField("ptr").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int contendedQuark = ss.getQuarkRelativeAndAdd(pidQuark,"Monitor");
            int monitorQuark = ss.getQuarkRelativeAndAdd(contendedQuark, ptr.toString());
            int nameQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(monitorName), nameQuark);
            int threadsQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "threads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            String threadName = threads.get(tid);
            int threadNameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(4), statusQuark);
            if(threadName != null) {
                ss.modifyAttribute(ts, TmfStateValue.newValueString(threadName), threadNameQuark);
            } else {
                ss.modifyAttribute(ts, TmfStateValue.newValueString(tid.toString()), threadNameQuark);
            }
        }
            break;
        case "jvm:monitor_waited": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            Long ptr  = (Long)event.getContent().getField("ptr").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int contendedQuark = ss.getQuarkRelativeAndAdd(pidQuark,"Monitor");
            int monitorQuark = ss.getQuarkRelativeAndAdd(contendedQuark, ptr.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "threads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "jvm:notify":
        case "jvm:notifyAll": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            String monitorName = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            Long ptr  = (Long)event.getContent().getField("ptr").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int contendedQuark = ss.getQuarkRelativeAndAdd(pidQuark,"Monitor");
            int monitorQuark = ss.getQuarkRelativeAndAdd(contendedQuark, ptr.toString());
            int nameQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(monitorName), nameQuark);
            int threadsQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "threads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            String threadName = threads.get(tid);
            int threadNameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(100), statusQuark);
            if(threadName != null) {
                ss.modifyAttribute(ts, TmfStateValue.newValueString(threadName), threadNameQuark);
            } else {
                ss.modifyAttribute(ts, TmfStateValue.newValueString(tid.toString()), threadNameQuark);
            }
            ss.modifyAttribute(ts+100, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "jvm:method_compile_begin": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"CompilerThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            String methodName  = event.getContent().getField("methodName").getValue().toString(); //$NON-NLS-1$
            String className  = event.getContent().getField("className").getValue().toString(); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(className+"/"+methodName), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(3), statusQuark);
        }
            break;
        case "jvm:method_compile_end": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid  = (Long)event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark,"CompilerThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "sched_switch": { //$NON-NLS-1$
            Long targettid  = (Long)event.getContent().getField("next_tid").getValue();
            Long prevtid  = (Long)event.getContent().getField("prev_tid").getValue();
        }
            break;
        default:
            /* Ignore other event types */
            break;
        }

    }
    @Override
    public ITmfStateProvider getNewInstance() {
        return new JavaStateProvider(getTrace(), fLayout);
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

}