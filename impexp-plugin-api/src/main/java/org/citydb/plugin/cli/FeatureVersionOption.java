package org.citydb.plugin.cli;

import org.citydb.config.project.query.simple.SimpleFeatureVersionFilter;
import org.citydb.config.project.query.simple.SimpleFeatureVersionFilterMode;
import picocli.CommandLine;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;

public class FeatureVersionOption implements CliOption {
    @CommandLine.Option(names = {"-r", "--feature-version"}, required = true,
            description = "Feature version: ${COMPLETION-CANDIDATES}.")
    private Version version;

    @CommandLine.Option(names = {"-R", "--feature-version-timestamp"}, paramLabel = "<timestamp[,timestamp]>",
            description = "Timestamp given as date <YYYY-MM-DD> or date-time <YYYY-MM-DDThh:mm:ss[(+|-)hh:mm]> with " +
                    "optional UTC offset. Use one timestamp with 'at' and two timestamps defining a time range " +
                    "with 'between'.")
    private String timestamp;

    private OffsetDateTime startDateTime;
    private OffsetDateTime endDateTime;
    private SimpleFeatureVersionFilter featureVersionFilter;

    public SimpleFeatureVersionFilter toFeatureVersionFilter(DatatypeFactory datatypeFactory) {
        if (featureVersionFilter != null) {
            if (startDateTime != null) {
                featureVersionFilter.setStartDate(toCalendar(startDateTime, datatypeFactory));
            }

            if (endDateTime != null) {
                featureVersionFilter.setEndDate(toCalendar(endDateTime, datatypeFactory));
            }
        }

        return featureVersionFilter;
    }

    enum Version {
        latest(SimpleFeatureVersionFilterMode.LATEST),
        at(SimpleFeatureVersionFilterMode.AT),
        between(SimpleFeatureVersionFilterMode.BETWEEN),
        all(null);

        private final SimpleFeatureVersionFilterMode mode;

        Version(SimpleFeatureVersionFilterMode mode) {
            this.mode = mode;
        }
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (version == Version.all) {
            if (timestamp != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The feature version '" + version + "' does not take a timestamp");
            } else {
                // no filter required to query all feature versions
                return;
            }
        }

        featureVersionFilter = new SimpleFeatureVersionFilter();
        featureVersionFilter.setMode(version.mode);

        if (timestamp != null) {
            if (version == Version.latest) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The feature version '" + version + "' does not take a timestamp");
            }

            String[] timestamps = timestamp.split(",");

            if (version == Version.at && timestamps.length != 1) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The feature version '" + version + "' requires only one timestamp");
            } else if (version == Version.between && timestamps.length != 2) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The feature version '" + version + "' requires two timestamps defining a time range");
            }

            DateTimeFormatter formatter = buildDateTimeFormatter();
            for (int i = 0; i < timestamps.length; i++) {
                try {
                    OffsetDateTime dateTime = OffsetDateTime.parse(timestamps[i], formatter);
                    if (i == 0) {
                        startDateTime = dateTime;
                    } else {
                        if (!dateTime.isAfter(startDateTime)) {
                            throw new CommandLine.ParameterException(commandLine,
                                    "Error: The start timestamp must be lesser than the end timestamp");
                        }

                        endDateTime = dateTime;
                    }
                } catch (DateTimeParseException e) {
                    throw new CommandLine.ParameterException(commandLine,
                            "A feature version timestamp must be in YYYY-MM-DD or YYYY-MM-DDThh:mm:ss[(+|-)hh:mm] " +
                                    "format but was '" + timestamps[i] + "'");
                }
            }
        } else {
            if (version == Version.at) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The feature version '" + version + "' requires a timestamp");
            } else if (version == Version.between) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The feature version '" + version + "' requires a start and an end timestamp");
            }
        }
    }

    private DateTimeFormatter buildDateTimeFormatter() {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .optionalStart()
                .appendLiteral('T')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .optionalStart()
                .appendOffsetId()
                .optionalEnd()
                .optionalEnd()
                .parseDefaulting(ChronoField.HOUR_OF_DAY, LocalTime.MAX.getHour())
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, LocalTime.MAX.getMinute())
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, LocalTime.MAX.getSecond())
                .parseDefaulting(ChronoField.OFFSET_SECONDS, OffsetDateTime.now().getOffset().getTotalSeconds())
                .toFormatter()
                .withResolverStyle(ResolverStyle.STRICT)
                .withChronology(IsoChronology.INSTANCE);
    }

    private XMLGregorianCalendar toCalendar(OffsetDateTime dateTime, DatatypeFactory datatypeFactory) {
        return datatypeFactory.newXMLGregorianCalendar(
                dateTime.getYear(),
                dateTime.getMonthValue(),
                dateTime.getDayOfMonth(),
                dateTime.getHour(),
                dateTime.getMinute(),
                dateTime.getSecond(),
                DatatypeConstants.FIELD_UNDEFINED,
                dateTime.getOffset() != ZoneOffset.UTC ?
                        dateTime.getOffset().getTotalSeconds() / 60 :
                        DatatypeConstants.FIELD_UNDEFINED);
    }
}
