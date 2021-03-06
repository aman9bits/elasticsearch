/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.search.query.CancellableCollector;
import org.elasticsearch.tasks.TaskCancelledException;
import org.elasticsearch.test.ESTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SearchCancellationTests extends ESTestCase {

    static Directory dir;
    static IndexReader reader;

    @BeforeClass
    public static void before() throws IOException {
        dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir);
        w.setDoRandomForceMerge(false); // we need 2 segments
        indexRandomDocuments(w, TestUtil.nextInt(random(), 2, 20));
        w.flush();
        indexRandomDocuments(w, TestUtil.nextInt(random(), 1, 20));
        reader = w.getReader();
        w.close();
    }

    private static void indexRandomDocuments(RandomIndexWriter w, int numDocs) throws IOException {
        for (int i = 0; i < numDocs; ++i) {
            final int numHoles = random().nextInt(5);
            for (int j = 0; j < numHoles; ++j) {
                w.addDocument(new Document());
            }
            Document doc = new Document();
            doc.add(new StringField("foo", "bar", Field.Store.NO));
            w.addDocument(doc);
        }
    }

    @AfterClass
    public static void after() throws IOException {
        IOUtils.close(reader, dir);
        dir = null;
        reader = null;
    }


    public void testLowLevelCancellableCollector() throws IOException {
        TotalHitCountCollector collector = new TotalHitCountCollector();
        AtomicBoolean cancelled = new AtomicBoolean();
        CancellableCollector cancellableCollector = new CancellableCollector(cancelled::get, true, collector);
        final LeafCollector leafCollector = cancellableCollector.getLeafCollector(reader.leaves().get(0));
        leafCollector.collect(0);
        cancelled.set(true);
        expectThrows(TaskCancelledException.class, () -> leafCollector.collect(1));
    }

    public void testCancellableCollector() throws IOException {
        TotalHitCountCollector collector = new TotalHitCountCollector();
        AtomicBoolean cancelled = new AtomicBoolean();
        CancellableCollector cancellableCollector = new CancellableCollector(cancelled::get, false, collector);
        final LeafCollector leafCollector = cancellableCollector.getLeafCollector(reader.leaves().get(0));
        leafCollector.collect(0);
        cancelled.set(true);
        leafCollector.collect(1);
        expectThrows(TaskCancelledException.class, () -> cancellableCollector.getLeafCollector(reader.leaves().get(1)));
    }

}
