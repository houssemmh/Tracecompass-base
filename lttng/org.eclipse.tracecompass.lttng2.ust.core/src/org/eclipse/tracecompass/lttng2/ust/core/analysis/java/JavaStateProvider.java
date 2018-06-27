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
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.event.*;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Houssem Daoud
 */
public class JavaStateProvider extends AbstractTmfStateProvider {

    /* Version of this state provider */
    class ThreadInfo {
        public Long pid;
        public String name;
        public String type;

        public ThreadInfo(Long pid, String name, String type) {
            this.pid = pid;
            this.name = name;
            this.type = type;
        }
    }

    private static final int VERSION = 1;
    private static final Long MINUS_ONE = Long.valueOf(-1);
    private final @NonNull IKernelAnalysisEventLayout fLayout;
    private ArrayList<Long> stopped_threads = new ArrayList<>();
    private HashMap<Long, ThreadInfo> threads = new HashMap<>();

    /**
     * Constructor
     *
     * @param trace
     *            trace
     */
    public JavaStateProvider(@NonNull ITmfTrace trace, IKernelAnalysisEventLayout layout) {
        super(trace, "Ust:Java"); //$NON-NLS-1$
        fLayout = layout;
        threads.put(Long.valueOf(8644), new ThreadInfo(Long.valueOf(8641), "Main Thread", "JavaThreads"));
        threads.put(Long.valueOf(6784), new ThreadInfo(Long.valueOf(8641), "Main Thread", "JavaThreads"));
    }

    private static Long getVtid(ITmfEvent event) {
        /* We checked earlier that the "vtid" context is present */
        ITmfEventField field = event.getContent().getField("context._vtid");
        if (field == null) {
            return MINUS_ONE;
        }
        return (Long) field.getValue();
    }

    private static int getCpu(ITmfEvent event) {
        /* We checked earlier that the "vtid" context is present */
        return ((CtfTmfEvent) event).getCPU();
    }

    // private static String substringAfterLast(String str, String separator) {
    // int pos = str.lastIndexOf(separator);
    // if (pos == -1 || pos == (str.length() - separator.length())) {
    // return "";
    // }
    // return str.substring(pos + separator.length());
    // }

    @Override
    protected void eventHandle(ITmfEvent event) {
        String name = event.getName();
        ITmfStateSystemBuilder ss = checkNotNull(getStateSystemBuilder());
        long ts = event.getTimestamp().toNanos();
        int cpu = getCpu(event);

        switch (name) {
        case "jvm:thread_start": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            Long iscompiler = (Long) event.getContent().getField("compiler").getValue(); //$NON-NLS-1$
            String threadtype;
            if (iscompiler == 0) {
                threadtype = "JavaThreads";
            } else {
                threadtype = "CompilerThreads";
            }
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, threadtype);
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            String threadname = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(threadname), nameQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
            threads.put(tid, new ThreadInfo(pid, threadname, threadtype));
        }
            break;
        case "jvm:thread_status": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            if (stopped_threads.contains(tid)) {
                break;
            }
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "JavaThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            Long status = (Long) event.getContent().getField("status").getValue(); //$NON-NLS-1$
            if (status == 2) {
                ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
                stopped_threads.add(tid);
            } else {
                ss.modifyAttribute(ts, TmfStateValue.newValueLong(status), statusQuark);
            }

            ThreadInfo pair = threads.get(tid);
            if (pair == null) {
                threads.put(tid, new ThreadInfo(pid, tid.toString(), "JavaThreads"));
            }

        }
            break;
        case "jvm:vmthread_start": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "VMThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            String threadname = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(threadname), nameQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
            threads.put(tid, new ThreadInfo(pid, threadname, "VMThreads"));
        }
            break;
        case "jvm:vmthread_stop": { //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            String targettid = event.getContent().getField("os_threadid").getValue().toString();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "VMThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, targettid);
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), nameQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "jvm:vmops_begin": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "VMThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            String operationName = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(operationName), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(1), statusQuark);
        }
            break;
        case "jvm:vmops_end": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "VMThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;

        case "jvm:gctaskthread_start": { //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            Long targettid = (Long) event.getContent().getField("os_threadid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "GCThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(gcthreadsQuark, targettid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            String threadname = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(threadname), nameQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
            threads.put(targettid, new ThreadInfo(pid, threadname, "GCThreads"));
        }
            break;
        case "jvm:gctask_start": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "GCThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(gcthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            String operationName = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(operationName), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(2), statusQuark);
        }
            break;
        case "jvm:gctask_end": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "GCThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(gcthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "jvm:contended_enter": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            String monitorName = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            Long ptr = (Long) event.getContent().getField("ptr").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int contendedQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Contention");
            int monitorQuark = ss.getQuarkRelativeAndAdd(contendedQuark, ptr.toString());
            int nameQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(monitorName), nameQuark);
            int threadsQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "threads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            String threadName;
            ThreadInfo pair = threads.get(tid);
            if (pair != null) {
                threadName = pair.name;
            } else {
                threadName = tid.toString();
            }
            int threadNameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(4), statusQuark);

            ss.modifyAttribute(ts, TmfStateValue.newValueString(threadName), threadNameQuark);
        }
            break;
        case "jvm:contended_entered": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            Long ptr = (Long) event.getContent().getField("ptr").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int contendedQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Contention");
            int monitorQuark = ss.getQuarkRelativeAndAdd(contendedQuark, ptr.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "threads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "jvm:monitor_wait": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            String monitorName = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            Long ptr = (Long) event.getContent().getField("ptr").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int contendedQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Monitor");
            int monitorQuark = ss.getQuarkRelativeAndAdd(contendedQuark, ptr.toString());
            int nameQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(monitorName), nameQuark);
            int threadsQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "threads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            String threadName;
            ThreadInfo pair = threads.get(tid);
            if (pair != null) {
                threadName = pair.name;
            } else {
                threadName = tid.toString();
            }
            int threadNameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(4), statusQuark);
            ss.modifyAttribute(ts, TmfStateValue.newValueString(threadName), threadNameQuark);
        }
            break;
        case "jvm:monitor_waited": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            Long ptr = (Long) event.getContent().getField("ptr").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int contendedQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Monitor");
            int monitorQuark = ss.getQuarkRelativeAndAdd(contendedQuark, ptr.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "threads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "jvm:notify":
        case "jvm:notifyAll": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            String monitorName = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            Long ptr = (Long) event.getContent().getField("ptr").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int contendedQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Monitor");
            int monitorQuark = ss.getQuarkRelativeAndAdd(contendedQuark, ptr.toString());
            int nameQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(monitorName), nameQuark);
            int threadsQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "threads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            String threadName;
            ThreadInfo pair = threads.get(tid);
            if (pair != null) {
                threadName = pair.name;
            } else {
                threadName = tid.toString();
            }
            int threadNameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(100), statusQuark);
            ss.modifyAttribute(ts, TmfStateValue.newValueString(threadName), threadNameQuark);
            ss.modifyAttribute(ts + 100, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "jvm:method_compile_begin": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "CompilerThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            String methodName = event.getContent().getField("methodName").getValue().toString(); //$NON-NLS-1$
            String className = event.getContent().getField("className").getValue().toString(); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueString(className + "/" + methodName), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(3), statusQuark);
        }
            break;
        case "jvm:method_compile_end": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "CompilerThreads");
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), infoQuark);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "sched_switch": { //$NON-NLS-1$
            Long targettid = (Long) event.getContent().getField("next_tid").getValue();
            String targetcomm = event.getContent().getField("next_comm").getValue().toString();
            Long prevtid = (Long) event.getContent().getField("prev_tid").getValue();
            ThreadInfo targetpair = threads.get(targettid);
            if (targetpair != null) {
                Long pid = targetpair.pid;
                try {
                    int pidQ = ss.getQuarkAbsolute(pid.toString());
                    int threadsQuark = ss.getQuarkRelative(pidQ, targetpair.type);
                    int tidQuark = ss.getQuarkRelative(threadsQuark, targettid.toString());
                    int kernelstatusQuark = ss.getQuarkRelative(tidQuark, "Kernel Status"); //$NON-NLS-1$
                    ss.modifyAttribute(ts, TmfStateValue.newValueLong(1001), kernelstatusQuark);
                } catch (AttributeNotFoundException e) {
                    e.printStackTrace();
                }
            }
            ThreadInfo prevpair = threads.get(prevtid);
            if (prevpair != null) {
                Long pid = prevpair.pid;
                try {
                    int pidQ = ss.getQuarkAbsolute(pid.toString());
                    int threadsQuark = ss.getQuarkRelative(pidQ, prevpair.type);
                    int tidQuark = ss.getQuarkRelative(threadsQuark, prevtid.toString());
                    int kernelstatusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "Kernel Status"); //$NON-NLS-1$
                    ss.modifyAttribute(ts, TmfStateValue.newValueLong(1002), kernelstatusQuark);
                } catch (AttributeNotFoundException e) {
                    e.printStackTrace();
                }
            }

            int cpusQ = ss.getQuarkAbsoluteAndAdd("CPUs");
            int cpuQ = ss.getQuarkRelativeAndAdd(cpusQ, String.valueOf(cpu));
            int cpustatusQ = ss.getQuarkRelativeAndAdd(cpuQ, "status");
            int cpuinfoQ = ss.getQuarkRelativeAndAdd(cpuQ, "info");
            if (targettid != 0) {
                ThreadInfo target = threads.get(targettid);
                if (target != null) {
                    switch (target.type) {
                    case "JavaThreads":
                        ss.modifyAttribute(ts, TmfStateValue.nullValue(), cpustatusQ);
                        ss.modifyAttribute(ts+1, TmfStateValue.newValueLong(1001), cpustatusQ);
                        break;
                    case "VMThreads":
                        ss.modifyAttribute(ts, TmfStateValue.nullValue(), cpustatusQ);
                        ss.modifyAttribute(ts+1, TmfStateValue.newValueLong(1002), cpustatusQ);
                        break;
                    case "GCThreads":
                        ss.modifyAttribute(ts, TmfStateValue.nullValue(), cpustatusQ);
                        ss.modifyAttribute(ts+1, TmfStateValue.newValueLong(1003), cpustatusQ);
                        break;
                    case "CompilerThreads":
                        ss.modifyAttribute(ts, TmfStateValue.nullValue(), cpustatusQ);
                        ss.modifyAttribute(ts+1, TmfStateValue.newValueLong(1004), cpustatusQ);
                        break;
                    default:
                        break;
                    }
                    ss.modifyAttribute(ts, TmfStateValue.newValueString(target.name+" ("+targettid.toString()+")"), cpuinfoQ);
                } else {
                    ss.modifyAttribute(ts, TmfStateValue.newValueLong(1005), cpustatusQ);
                    ss.modifyAttribute(ts, TmfStateValue.newValueString(targetcomm+" ("+targettid.toString()+")"), cpuinfoQ);
                }

            } else {
                ss.modifyAttribute(ts, TmfStateValue.nullValue(), cpustatusQ);
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
        return new JavaStateProvider(getTrace(), fLayout);
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

}
