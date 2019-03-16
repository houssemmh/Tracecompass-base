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
import org.eclipse.osgi.framework.util.ArrayMap;
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
import org.eclipse.tracecompass.tmf.ctf.core.CtfEnumPair;

import java.awt.List;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

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

    class SectorInfo {
        public Long sector;
        public Long ts_start;
        public Long ts_end;
        public Long nr_sector;

        public SectorInfo(Long sector, Long ts_start, Long nr_sector) {
            this.sector = sector;
            this.ts_start = ts_start;
            this.nr_sector = nr_sector;
        }
    }

    class DiskInfo {
        public HashMap<Long, SectorInfo> sectors;
        public Long dev;

        public DiskInfo(Long dev) {
            this.dev = dev;
            this.sectors = new HashMap<>();
        }
    }

    class PacketInfo {
        public Long size;
        public String daddr;

        public PacketInfo(String daddr, Long size) {
            this.daddr = daddr;
            this.size = size;
        }
    }

    class NetworkInfo {
        public ArrayList<PacketInfo> packets;
        public String name;

        public NetworkInfo(String name) {
            this.name = name;
            this.packets = new ArrayList<>();
        }
    }

    class HostInfo {
        public String name;
        public HashMap<Long, DiskInfo> disks_info;
        public HashMap<String, NetworkInfo> networks_info;

        public HostInfo(String name) {
            this.name = name;
            disks_info = new HashMap<>();
            networks_info = new HashMap<>();
        }
    }

    private static final int VERSION = 1;

    // private static final Long MINUS_ONE = Long.valueOf(-1);
    // private ArrayList<Long> stopped_threads = new ArrayList<>();
    // private HashMap<Long, ThreadInfo> threads = new HashMap<>();
    private final @NonNull IKernelAnalysisEventLayout fLayout;
    private HashMap<String, HostInfo> hosts_info = new HashMap<>();
    Pile pile = null;

    private BufferedWriter fBw;

    private void show_disk_stats(long ts) {
        synchronized (this) {
            System.out.println("####################################### " + ts);
            System.out.println("######## Disk");
            for (HostInfo hostinfo : hosts_info.values()) {
                System.out.println("Host: " + hostinfo.name);
                for (DiskInfo disk : hostinfo.disks_info.values()) {
                    long time = 0;
                    long size = 0;
                    try {
                        for (SectorInfo sect : disk.sectors.values()) {
                            time = time + sect.ts_end - sect.ts_start;
                            size = size + sect.nr_sector;
                        }
                    } catch (Exception e) {
                    }
                    System.out.println("    Disk: " + disk.dev);
                    System.out.println("        requests : " + disk.sectors.size());
                    System.out.println("        time : " + time);
                    System.out.println("        size : " + size / 2024 + " MB");
                }
            }
        }
    }

    private void show_network_stats(long ts) {
      synchronized (this) {
        System.out.println("######## Network");
        // Delete file content
        String edgesname = "/home/houssemmh/Documents/edges.csv";
        try {
            FileWriter fw = new FileWriter(edgesname);
            fBw = new BufferedWriter(fw);
            fBw.write("from,to,value");
            fBw.newLine();
            fBw.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        String nodesname = "/home/houssemmh/Documents/nodes.csv";
        try {
            FileWriter fw = new FileWriter(nodesname);
            fBw = new BufferedWriter(fw);
            fBw.write("id,label,value");
            fBw.newLine();
            fBw.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        HashMap<String, NetworkInfo> networks_info = new HashMap<>();
        for (HostInfo hostinfo : hosts_info.values()) {
            for (NetworkInfo net : hostinfo.networks_info.values()) {
                networks_info.put(net.name, net);
            }
        }

        //System.out.println(file.getAbsolutePath());
        for (HostInfo hostinfo : hosts_info.values()) {
            // System.out.println("Host: " + hostinfo.name);
            for (NetworkInfo net : hostinfo.networks_info.values()) {
                System.out.println("    Network: " + net.name);
                long size = 0;
                HashMap<String, Long> destinations = new HashMap<>();
                for (PacketInfo packet : net.packets) {
                    if (!networks_info.containsKey(packet.daddr)) {
                        continue;
                    }
                    if (net.name.equals(packet.daddr)) {
                        continue;
                    }
                    size = size + packet.size;
                    long count = destinations.containsKey(packet.daddr) ? destinations.get(packet.daddr) : 0;
                    destinations.put(packet.daddr, count + packet.size);
                }
                try {
                    FileWriter fw = new FileWriter(nodesname,true);
                    fBw = new BufferedWriter(fw);
                    fBw.write(net.name+","+net.name+","+size);
                    fBw.newLine();
                    fBw.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                System.out.println("        Packets numbers: " + net.packets.size());
                System.out.println("        Total transferred : " + size);
                for (Entry<String, Long> dest : destinations.entrySet()) {
                    System.out.println("            --> " + dest.getKey() + " : " + dest.getValue());
                    try {
                        FileWriter fw = new FileWriter(edgesname,true);
                        fBw = new BufferedWriter(fw);
                        fBw.write(net.name+","+dest.getKey()+","+dest.getValue());
                        fBw.newLine();
                        fBw.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
      }
    }

    /**
     * Constructor
     *
     * @param trace
     *            trace
     * @param layout
     *            layout
     */
    @SuppressWarnings("null")
    public JavaStateProvider(@NonNull ITmfTrace trace, IKernelAnalysisEventLayout layout) {
        super(trace, "Ust:Java"); //$NON-NLS-1$
        fLayout = layout;
    }

    /*
     * private static Long getVtid(ITmfEvent event) { // We checked earlier that the
     * "vtid" context is present ITmfEventField field =
     * event.getContent().getField("context._vtid"); //$NON-NLS-1$ if (field ==
     * null) { return MINUS_ONE; } return (Long) field.getValue(); }
     */

    private static int getCpu(ITmfEvent event) {
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
        String hostname = event.getTrace().getName();
        ITmfStateSystemBuilder ss = checkNotNull(getStateSystemBuilder());
        long ts = event.getTimestamp().toNanos();
        HostInfo hostinfo;
        if (hosts_info.containsKey(hostname)) {
            hostinfo = hosts_info.get(hostname);
        } else {
            hostinfo = new HostInfo(hostname);
            hosts_info.put(hostname, hostinfo);
        }
        if (ts == event.getTrace().getEndTime().getValue()) {
            show_network_stats(ts);
        }

        switch (name) {
        case "block_rq_insert": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("context._tid").getValue(); //$NON-NLS-1$
            Long dev = (Long) event.getContent().getField("dev").getValue(); //$NON-NLS-1$
            Long sector = (Long) event.getContent().getField("sector").getValue(); //$NON-NLS-1$
            Long nr_sector = (Long) event.getContent().getField("nr_sector").getValue(); //$NON-NLS-1$
            DiskInfo diskinfo;
            if (hostinfo.disks_info.containsKey(dev)) {
                diskinfo = hostinfo.disks_info.get(dev);
            } else {
                diskinfo = new DiskInfo(dev);
                hostinfo.disks_info.put(dev, diskinfo);
            }

            diskinfo.sectors.put(sector, new SectorInfo(sector, ts, nr_sector));
        }
            break;
        case "block_rq_complete": { //$NON-NLS-1$
            Long dev = (Long) event.getContent().getField("dev").getValue(); //$NON-NLS-1$
            Long sector = (Long) event.getContent().getField("sector").getValue(); //$NON-NLS-1$
            if (hostinfo.disks_info.containsKey(dev)) {
                DiskInfo diskinfo = hostinfo.disks_info.get(dev);
                if (diskinfo.sectors.containsKey(sector)) {
                    SectorInfo sect = diskinfo.sectors.get(sector);
                    sect.ts_end = ts;
                    // System.out.println("sector " + sector + " is completed at " + ts);
                }
            }
        }
            break;

        case "net_dev_queue": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("context._tid").getValue(); //$NON-NLS-1$
            String daddr = event.getContent().getField("network_header").getField("ipv4").getField("daddr").toString().replaceAll("\\s+", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            daddr = daddr.split("daddr=")[1].split("\\[")[1].split("\\]")[0]; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            daddr = daddr.replace(",", ".");
            String saddr = event.getContent().getField("network_header").getField("ipv4").getField("saddr").toString().replaceAll("\\s+", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            saddr = saddr.split("saddr=")[1].split("\\[")[1].split("\\]")[0]; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            saddr = saddr.replace(",", ".");
            String net_interface = (String) event.getContent().getField("name").getValue(); //$NON-NLS-1$
            net_interface = saddr;
            //Long dest_port = (Long)event.getContent().getField("network_header").getField("ipv4").getField("transport_header").getField("tcp").getField("dest_port").getValue();
            Long size = (Long) event.getContent().getField("len").getValue(); //$NON-NLS-1$

            NetworkInfo netinfo;
            if (hostinfo.networks_info.containsKey(net_interface)) {
                netinfo = hostinfo.networks_info.get(net_interface);
            } else {
                netinfo = new NetworkInfo(net_interface);
                hostinfo.networks_info.put(net_interface, netinfo);
            }

            netinfo.packets.add(new PacketInfo(daddr, size));
        }
            break;
        case "osd:opwq_process_start": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("context._vtid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            int hostQuark = ss.getQuarkAbsoluteAndAdd(hostname);
            int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
            int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueInt(1), statusQuark);
        }
            break;
        case "osd:opwq_process_finish": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("context._vtid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            int hostQuark = ss.getQuarkAbsoluteAndAdd(hostname);
            int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
            int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "bfd_fsm__state_change": { //$NON-NLS-1$
            Long session_id = (Long) event.getContent().getField("sid_m").getValue(); //$NON-NLS-1$
            String nxt_state =  ((CtfEnumPair) event.getContent().getField("next_state").getValue()).getStringValue(); //$NON-NLS-1$
            int hostQuark = ss.getQuarkAbsoluteAndAdd("bfd_fsm_sid");
            int sessionQuark = ss.getQuarkRelativeAndAdd(hostQuark, String.format("%03d",session_id));
            int statusQuark = ss.getQuarkRelativeAndAdd(sessionQuark, "status"); //$NON-NLS-1$
            if (nxt_state.equalsIgnoreCase("Init")) {
            ss.modifyAttribute(ts, TmfStateValue.newValueInt(11), statusQuark);
            }
            if (nxt_state.equalsIgnoreCase("Down")) {
            ss.modifyAttribute(ts, TmfStateValue.newValueInt(12), statusQuark);
            }
            if (nxt_state.equalsIgnoreCase("Up")) {
            ss.modifyAttribute(ts, TmfStateValue.newValueInt(13), statusQuark);
            }
        }
            break;

        case "hal__oam_prot_group": { //$NON-NLS-1$
            Long session_id = (Long) event.getContent().getField("prot_id").getValue(); //$NON-NLS-1$
            Long nxt_state =  (Long) event.getContent().getField("cur_stat_id").getValue();
            int hostQuark = ss.getQuarkAbsoluteAndAdd("hal__oam_prot_group");
            int sessionQuark = ss.getQuarkRelativeAndAdd(hostQuark, String.format("%03d",session_id));
            int statusQuark = ss.getQuarkRelativeAndAdd(sessionQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(nxt_state), statusQuark);
            }
                break;

        case "zipkin:timestamp": { //$NON-NLS-1$
            // Long tid = (Long) event.getContent().getField("context._vtid").getValue();
            // //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            String eventStr = (String) event.getContent().getField("event").getValue();
            int hostQuark = ss.getQuarkAbsoluteAndAdd(hostname);
            switch (eventStr) {
            case "async enqueueing message": {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, "msg");
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
                ss.modifyAttribute(ts, TmfStateValue.newValueInt(2), statusQuark);
            }
                break;
            case "async writing message": {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, "msg");
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
                ss.modifyAttribute(ts, TmfStateValue.newValueInt(3), statusQuark);
            }
                break;
            case "osd op reply": {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, "msg");
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
                ss.updateOngoingState(TmfStateValue.newValueInt(4), statusQuark);
            }
                break;
            case "finish": {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, "msg");
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
                ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
            }
                break;
            case "message destructed": {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, "msg");
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
                ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
            }
                break;

            default:
                /* Ignore other event types */
                break;
            }
        }
            break;

        default:
            /* Ignore other event types */
            break;
        }
    }

    /**
     * @author houssemmh
     *
     */
    public class Pile {
        private Integer num_elements = 0; // Employee name
        private HashMap<Long, Long> queue = new HashMap<>();
        private HashMap<Long, Long> requests = new HashMap<>();
        private ITmfStateSystemBuilder ss;
        private int diskQ;

        /**
         * @param ss
         * @param diskQ
         *
         */
        public Pile(ITmfStateSystemBuilder ss, int diskQ) {
            this.ss = ss;
            this.diskQ = diskQ;
        }

        private Long findFirstAvailable() {
            for (int i = 0; i < num_elements; i++) {
                if (queue.get(Long.valueOf(i)) == null) {
                    return Long.valueOf(i);
                }
            }
            return Long.valueOf(0);
        }

        /**
         * @param sector
         * @param ts
         */
        public void insert(Long sector, long ts, Long size, String processname, boolean isjava) {
            if (sector.longValue() == 738877456) {
                return;
            }
            String name = processname;
            if (name.equals("fio")) {
                name = "update.py";
            }
            Long position = findFirstAvailable();
            queue.put(position, sector);
            requests.put(sector, position);
            int slotQ = ss.getQuarkRelativeAndAdd(diskQ, String.format("%03d", position));
            int statusQ = ss.getQuarkRelativeAndAdd(slotQ, "status");
            int infoQ = ss.getQuarkRelativeAndAdd(slotQ, "info");
            if (isjava) {
                ss.modifyAttribute(ts, TmfStateValue.newValueLong(1001), statusQ);
                ss.modifyAttribute(ts, TmfStateValue.newValueString(name + " (" + String.valueOf(size / 2) + "KB)"), infoQ);
            } else {
                ss.modifyAttribute(ts, TmfStateValue.newValueLong(1002), statusQ);
                ss.modifyAttribute(ts, TmfStateValue.newValueString(name + " (" + String.valueOf(size / 2) + "KB)"), infoQ);
            }
            num_elements++;
        }

        /**
         * @param sector
         * @param ts
         */
        public void remove(Long sector, long ts) {
            Long position = requests.get(sector);
            if (position != null) {
                requests.put(position, null);
                queue.put(position, null);
                int slotQ = ss.getQuarkRelativeAndAdd(diskQ, String.format("%03d", position));
                int statusQ = ss.getQuarkRelativeAndAdd(slotQ, "status");
                int infoQ = ss.getQuarkRelativeAndAdd(slotQ, "info");
                ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQ);
                ss.modifyAttribute(ts, TmfStateValue.nullValue(), infoQ);

            }
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
