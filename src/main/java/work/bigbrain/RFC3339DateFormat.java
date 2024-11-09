package work.bigbrain;

import com.fasterxml.jackson.databind.util.StdDateFormat;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Date;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatterBuilder;

public class RFC3339DateFormat extends StdDateFormat {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .toFormatter();

    @Override
    public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
        String value = ZonedDateTime.from(date.toInstant().atZone(getTimeZone().toZoneId()))
            .format(formatter);
        toAppendTo.append(value);
        return toAppendTo;
    }

    @Override
    public Date parse(String source, ParsePosition pos) {
        // 使用父类的解析方法，因为StdDateFormat已经能很好地处理ISO-8601格式
        return super.parse(source, pos);
    }

    @Override
    public RFC3339DateFormat clone() {
        return new RFC3339DateFormat();
    }
}