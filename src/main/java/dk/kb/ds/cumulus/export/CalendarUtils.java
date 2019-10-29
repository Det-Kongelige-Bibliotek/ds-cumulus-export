package dk.kb.ds.cumulus.export;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Utility class for calendar issues.
 */
public class CalendarUtils {

    /** constructor to prevent instantiation of utility class. */
    protected CalendarUtils() {}

    /** A single instance of the DatatypeFactory to prevent overlap from recreating it too often.*/
    private static DatatypeFactory factory = null;
    private static final String FALSE = "false";

    /**
     * Turns a date into a XMLGregorianCalendar.
     *
     * @param date The date.
     * @return The XMLGregorianCalendar.
     */
    public static XMLGregorianCalendar getXmlGregorianCalendar(Date date) {
        try {
            if(factory == null) {
                factory = DatatypeFactory.newInstance();
            }

            GregorianCalendar gc = new GregorianCalendar();
            gc.setTime(date);
            return factory.newXMLGregorianCalendar(gc);
        } catch (Exception e) {
            IllegalStateException res = new IllegalStateException("Could not create XML date for the date '"
                + date + "'.", e);
            throw res;
        }
    }

    /**
     * @return The current date in the XML date format.
     */
    public static String getCurrentDate() {
        return getXmlGregorianCalendar(new Date()).toString();
    }

    /**
     * Retrieves a date in the XML format, which needs to be transformed from another given format.
     * @param format The format of the given date.
     * @param dateString The given date to transform.
     * @return The given date in the XML date format.
     */
    public static String getDateTime(String format, String dateString) {
        SimpleDateFormat formatter = new SimpleDateFormat(format);
        try {
            Date date = formatter.parse(dateString);
            return getXmlGregorianCalendar(date).toString();
        } catch (ParseException e) {
            try {
                SimpleDateFormat formatter2 = new SimpleDateFormat(format, Locale.US);
                Date date2 = formatter2.parse(dateString);
                return getXmlGregorianCalendar(date2).toString();
            } catch (ParseException e2) {
                return FALSE;
//                IllegalStateException res = new IllegalStateException("Cannot parse date '" + dateString
//                    + "' in format '" + format + "'. " + "Caught exceptions: " + e + " , " + e2, e2);
//                throw res;
            }
        }
    }

    /**
     * Converts gregorian date .
     * @param datetimeFromCumulus The originating date from Cumulus.
     * @return The date in solr date_range format or text value ??
     */

    public static String convertDatetimeFormat(String datetimeFromCumulus) throws DateTimeException {

        String[] patternList = {"yyyy-MM-dd", "yyyy-MM", "yyyy"};
        String pattern;

        String gregorianDate = getDateTime(patternList[0], datetimeFromCumulus);
        pattern = patternList[0];

        if (gregorianDate.equals(FALSE)){
            pattern = patternList[0];
            gregorianDate = getDateTime("yyyy.MM.dd", datetimeFromCumulus);
        }
        if (gregorianDate.equals(FALSE)){
            pattern = patternList[1];
            gregorianDate = getDateTime("yyyy.MM", datetimeFromCumulus);
        }
        if (gregorianDate.equals(FALSE)){
            pattern = patternList[1];
            gregorianDate = getDateTime(pattern, datetimeFromCumulus);

        }
        if (gregorianDate.equals(FALSE)){
            pattern = patternList[2];
            gregorianDate = getDateTime(pattern, datetimeFromCumulus);
        }
        if (gregorianDate.equals(FALSE)){
            return datetimeFromCumulus;
            //Return the string value of År, i.e. no appropriate date format found
//            TODO: How should this be handled by solr
        }

        // Convert to solr date range format

        LocalDateTime createdDateFormatted = LocalDateTime.parse(gregorianDate, DateTimeFormatter.ISO_DATE_TIME);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(pattern);

        final String format = createdDateFormatted.format(timeFormatter);

        return format;
    }

}
