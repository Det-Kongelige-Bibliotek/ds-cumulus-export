/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.kb.ds.cumulus.export;

import dk.kb.cumulus.CumulusQuery;
import dk.kb.cumulus.CumulusRecord;
import dk.kb.cumulus.CumulusRecordCollection;
import dk.kb.cumulus.CumulusServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

/**
 * Extracts field- and content-statistics from the configured Cumulus collection.
 */
public class CumulusStats {
    private static final Logger log = LoggerFactory.getLogger(CumulusStats.class);

    public static final int MAX_OUTPUT_VALUES = 20;

    private final Map<String, FieldStat> fieldStats = new HashMap<>();
    private int recordCounter = 0;
    private int problematicRecords = 0;

    public static void main(String[] args) throws Exception {
        new CumulusStats();
    }
    private CumulusStats() throws Exception {
        try (CumulusServer server = new CumulusServer(Configuration.getCumulusConf())) {
            Properties exportpro = new Properties();
            exportpro.load(CumulusExport.class.getClassLoader().getResourceAsStream("cumulusExport.properties"));
            boolean limited = Boolean.parseBoolean(exportpro.getProperty("limited"));
            int maxRecords = Integer.parseInt(exportpro.getProperty("counter"));

            String myCatalog = Configuration.getCumulusConf().getCatalogs().get(0);
            CumulusQuery query = CumulusQuery.getQueryForAllInCatalog(myCatalog);
            log.info("Requesting catalog '{}' with query '{}' from server for statistics", myCatalog, query);
            CumulusRecordCollection records = server.getItems(myCatalog, query);
            StreamSupport.stream(records.spliterator(), false).
                limit(limited ? maxRecords : Long.MAX_VALUE).
                forEach(this::collect);
            printStats();
        }
    }

    private final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
    {
        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true); // getElementText returns full element text
        xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
    }
    private void collect(CumulusRecord record) {
        recordCounter++;
        // We really need to bend backwards here to get all the fields.
        // A simple "record.getAllFieldNames()" would help tremendously!
        try (ByteArrayOutputStream recordContent = new ByteArrayOutputStream()) {
            record.writeFieldMetadata(recordContent);

            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(
                new StringReader(recordContent.toString("utf-8")));
            while (reader.hasNext()) {
                reader.next();
                if (reader.isStartElement() && "field".equals(reader.getLocalName())) {
                    collectField(reader);
                }
            }
        } catch (Exception e) {
            log.warn("Exception while processing Cumulus record");
            problematicRecords++;
        }
    }

    private void collectField(XMLStreamReader reader) throws XMLStreamException {
        final String dataType = reader.getAttributeValue("", "data-type");
        final String field = reader.getAttributeValue("", "name");
        if (field == null || field.isEmpty()) {
            log.warn("Encountered <field...> with no name");
            return;
        }
        while (reader.hasNext()) {
            if (reader.isEndElement() && "field".equals(reader.getLocalName())) {
                break;
            }
            if (reader.isStartElement() && "value".equals(reader.getLocalName())) {
                final String value = reader.getElementText();
                collectStat(field, dataType, value);
            }
            reader.next();
        }
    }

    private void collectStat(String field, String dataType, String value) {
        FieldStat fs = fieldStats.get(field);
        if (fs == null) {
            fs = new FieldStat(field, dataType);
            fieldStats.put(field, fs);
        }
        fs.addValue(value);
    }

    private void printStats() {
        System.out.println("Processed " + recordCounter + " out of which " + problematicRecords +
                           " were skipped due to technical problems. Found " + fieldStats.size() + " unique fields:");
        fieldStats.forEach((fn, fs) -> System.out.println(fs.toString()));
    }

    private class FieldStat {
        private final String field;
        private final String dataType;
        private final Map<String, AtomicInteger> values = new HashMap<>();

        public FieldStat(String field, String dataType) {
            this.field = field;
            this.dataType = dataType;
        }

        /**
         * Add the value to the fieldStats for the field.
         * @param value a value from a Cumulus field.
         * @return true if the value was already tracked.
         */
        public boolean addValue(String value) {
            AtomicInteger count = values.get(value);
            if (count == null) {
                count = new AtomicInteger(0);
                values.put(value, count);
            }
            return count.incrementAndGet() > 1;
        }

        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("*** Field name='").append(field).append("', type='").append(dataType);
            sb.append("', unique_values=").append(values.size()).append("\n");
            values.entrySet().stream().
                sorted(Comparator.comparingInt(e -> -e.getValue().get())). // - to reverse the order
                limit(MAX_OUTPUT_VALUES == -1 ? Integer.MAX_VALUE : MAX_OUTPUT_VALUES).
                forEach(entry -> sb.append(entry.getValue()).append(": ").append(entry.getKey()).append("\n"));
            return sb.toString();
        }
    }
}
