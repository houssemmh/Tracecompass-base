/*******************************************************************************
 * Copyright (c) 2017, 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.analysis.os.linux.core.threadstatus;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.TimeGraphEntryModel;

/**
 * Thread Status entry model.
 *
 * @author Simon Delisle
 */
@SuppressWarnings("restriction")
public class ThreadEntryModel extends TimeGraphEntryModel {

    /**
     * {@link ThreadEntryModel} builder, we use this to be able to reassign
     * parentIds to be able to rebuild the PS tree even when inactive threads are
     * filtered.
     */
    public static final class Builder {
        private final long fId;
        private @NonNull String fName;
        private final long fStartTime;
        private long fEndTime;
        private final int fPid;
        private int fPpid;

        /**
         * Constructor
         *
         * @param id
         *            The unique ID for this Entry model for its trace
         * @param name
         *            the thread name
         * @param start
         *            the thread's start time
         * @param end
         *            the thread's end time
         * @param pid
         *            the thread's PID
         * @param ppid
         *            the thread's PPID
         */
        public Builder(long id, @NonNull String name, long start, long end, int pid, int ppid) {
            fId = id;
            fName = name;
            fStartTime = start;
            fEndTime = end;
            fPid = pid;
            fPpid = ppid;
        }

        /**
         * Get the unique ID for this entry / builder
         *
         * @return this entry's unique ID
         */
        public long getId() {
            return fId;
        }

        /**
         * Get this entry / builder's start time
         *
         * @return the start time
         */
        public long getStartTime() {
            return fStartTime;
        }

        /**
         * Get this entry/builder's end time
         *
         * @return the end time
         */
        public long getEndTime() {
            return fEndTime;
        }

        /**
         * Get this entry/builder's parent PID
         *
         * @return the PPID
         */
        public int getPpid() {
            return fPpid;
        }

        /**
         * Update this entry / builder's name
         *
         * @param name
         *            the new name
         */
        public void setName(@NonNull String name) {
            fName = name;
        }

        /**
         * Update this entry / builder's end time
         *
         * @param endTime
         *            the new end time
         */
        public void setEndTime(long endTime) {
            fEndTime = Long.max(fEndTime, endTime);
        }

        /**
         * Update this entry / builder's PPID
         *
         * @param ppid
         *            the new PPID
         */
        public void setPpid(int ppid) {
            fPpid = ppid;
        }

        /**
         * Build the {@link ThreadEntryModel} from the builder, specify the parent id
         * here to avoid race conditions
         *
         * @param parentId
         *            parent ID to use when building this entry
         * @return the relevant {@link ThreadEntryModel} or throw a
         *         {@link NullPointerException} if the parent Id is not set.
         */
        public ThreadEntryModel build(long parentId) {
            return new ThreadEntryModel(fId, parentId, fName, fStartTime, fEndTime, fPid, fPpid);
        }
    }

    private final int fThreadId;
    private final int fParentThreadId;

    /**
     * Constructor
     *
     * @param id
     *            The unique ID for this Entry model for its trace
     * @param parentId
     *            this Entry model's ID
     * @param name
     *            the thread name
     * @param start
     *            the thread's start time
     * @param end
     *            the thread's end time
     * @param pid
     *            the thread's PID
     * @param ppid
     *            the thread's PPID
     */
    public ThreadEntryModel(long id, long parentId, @NonNull String name, long start, long end, int pid, int ppid) {
        super(id, parentId, name, start, end);
        fThreadId = pid;
        fParentThreadId = ppid;
    }

    /**
     * Gets the entry thread ID
     *
     * @return Thread ID
     */
    public int getThreadId() {
        return fThreadId;
    }

    /**
     * Gets the parent entry thread ID
     *
     * @return Parent thread ID
     */
    public int getParentThreadId() {
        return fParentThreadId;
    }

    @Override
    public @NonNull String toString() {
        return "<name=" + getName() + " id=" + getId() + " parentId=" + getParentId() //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + " start=" + getStartTime() + " end=" + getEndTime() //$NON-NLS-1$ //$NON-NLS-2$
                + " TID=" + fThreadId + " PTID=" + fParentThreadId + ">"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

}
