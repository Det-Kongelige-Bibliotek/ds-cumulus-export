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
package dk.kb.ds.cumulus.export.converters;

import com.canto.cumulus.fieldvalue.AssetReference;
import dk.kb.cumulus.CumulusRecord;
import dk.kb.ds.cumulus.export.FieldMapper;
import dk.kb.ds.cumulus.export.YAML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts String content by either copying verbatim or splitting on newline.
 * Newline splitting is handled by {@link Converter}.
 */
public class StringConverter extends Converter {
    private static final Logger log = LoggerFactory.getLogger(StringConverter.class);

    public static final String CONF_PATTERN = "pattern";
    public static final String CONF_REPLACEMENT = "replacement";

    private Pattern pattern;
    private String replacement;

    public static void register() {
        ConverterFactory.registerCreator("string", StringConverter::new);
    }

    public StringConverter(YAML config) {
        super(config);
        pattern = config.containsKey(CONF_PATTERN) ? Pattern.compile(config.getString(CONF_PATTERN)) : null;
        replacement = config.getString(CONF_REPLACEMENT, null);
        if (pattern != null) {
            log.debug("");
            if (replacement == null) {
                throw new IllegalArgumentException(
                    "A pattern '" + pattern.pattern() + "' was specified, but a replacement is missing");
            }
        }
    }

    @Override
    public void convertImpl(CumulusRecord record, List<FieldMapper.FieldValue> resultList) {
        switch (sourceType) {
            case string: {
                convertImpl(getAsString(record), resultList);
                break;
            }
            case assetReference: {
                AssetReference ar = record.getAssetReference(source);
                convertImpl(ar.getDisplayString(), resultList);
                break;
            }
            default: throw new UnsupportedOperationException("The source type '" + sourceType + "' is not supported");
        }
    }

    void convertImpl(String input, List<FieldMapper.FieldValue> resultList) {
        if (input == null) {
            return;
        }
        if (pattern == null) { // Plain string copying
            addValue(input, resultList);
            return;
        }

        // Pattern matching & replacement
        Matcher matcher = pattern.matcher(input);
        if (!matcher.matches()) {
            return;
        }
        addValue(matcher.replaceAll(replacement), resultList);
    }
}
