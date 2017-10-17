/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.query.h2.database.io;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.pagemem.PageUtils;
import org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.BPlusIO;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.BPlusLeafIO;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.IOVersions;
import org.apache.ignite.internal.processors.query.h2.database.H2Tree;
import org.apache.ignite.internal.processors.query.h2.opt.GridH2Row;
import org.apache.ignite.internal.processors.query.h2.opt.GridH2SearchRow;

import static org.apache.ignite.internal.processors.cache.mvcc.CacheCoordinatorsProcessor.assertMvccVersionValid;

/**
 * Leaf page for H2 row references.
 */
public abstract class AbstractH2LeafIO extends BPlusLeafIO<GridH2SearchRow> implements H2RowLinkIO {
    /**
     * @param type Page type.
     * @param ver Page format version.
     * @param itemSize Single item size on page.
     */
    AbstractH2LeafIO(int type, int ver, int itemSize) {
        super(type, ver, itemSize);
    }

    /** {@inheritDoc} */
    @Override public boolean storeMvccInfo() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public final void storeByOffset(long pageAddr, int off, GridH2SearchRow row) {
        GridH2Row row0 = (GridH2Row)row;

        assert row0.link() != 0;

        PageUtils.putLong(pageAddr, off, row0.link());

        if (storeMvccInfo()) {
            long mvccCrdVer = row.mvccCoordinatorVersion();
            long mvccCntr = row.mvccCounter();

            assert assertMvccVersionValid(mvccCrdVer, mvccCntr);

            PageUtils.putLong(pageAddr, off + 8, mvccCrdVer);
            PageUtils.putLong(pageAddr, off + 16, mvccCntr);
        }
    }

    /** {@inheritDoc} */
    @Override public final void store(long dstPageAddr, int dstIdx, BPlusIO<GridH2SearchRow> srcIo, long srcPageAddr, int srcIdx) {
        assert srcIo == this;

        int off = offset(dstIdx);

        PageUtils.putLong(dstPageAddr, off, getLink(srcPageAddr, srcIdx));

        if (storeMvccInfo()) {
            long mvccCrdVer = getMvccCoordinatorVersion(srcPageAddr, srcIdx);
            long mvccCntr = getMvccCounter(srcPageAddr, srcIdx);

            assert assertMvccVersionValid(mvccCrdVer, mvccCntr);

            PageUtils.putLong(dstPageAddr, off + 8, mvccCrdVer);
            PageUtils.putLong(dstPageAddr, off + 16, mvccCntr);
        }
    }

    /** {@inheritDoc} */
    @Override public final GridH2SearchRow getLookupRow(BPlusTree<GridH2SearchRow,?> tree, long pageAddr, int idx)
        throws IgniteCheckedException {
        long link = getLink(pageAddr, idx);

        if (storeMvccInfo()) {
            long mvccCrdVer = getMvccCoordinatorVersion(pageAddr, idx);
            long mvccCntr = getMvccCounter(pageAddr, idx);

            return ((H2Tree)tree).getRowFactory().getMvccRow(link, mvccCrdVer, mvccCntr);
        }

        return ((H2Tree)tree).getRowFactory().getRow(link);
    }

    /** {@inheritDoc} */
    @Override public long getLink(long pageAddr, int idx) {
        return PageUtils.getLong(pageAddr, offset(idx));
    }

    /** {@inheritDoc} */
    @Override public long getMvccCoordinatorVersion(long pageAddr, int idx) {
        assert storeMvccInfo();

        return PageUtils.getLong(pageAddr, offset(idx) + 8);
    }

    /** {@inheritDoc} */
    @Override public long getMvccCounter(long pageAddr, int idx) {
        assert storeMvccInfo();

        return PageUtils.getLong(pageAddr, offset(idx) + 16);
    }
}
