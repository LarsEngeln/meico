package meico.mpm.elements;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.*;

/**
 * This class interfaces temporal domain with a value
 * @author Lars Engeln
 */
public class TemporalValue {
    /**
     * Enumeration of supported domains
     */
    public enum Domain {
        UNKNOWN,
        RELATIVE,
        MILLISECONDS,
        TICKS,
        NOTELENGTH // 8th, 16th, ..
    }
    private static final Map<Domain, String> domainStrings;
    static {
        Map<Domain, String> map = new HashMap<>();
        map.put(Domain.MILLISECONDS, "ms");
        map.put(Domain.NOTELENGTH, "th");
        map.put(Domain.RELATIVE, "%");
        map.put(Domain.TICKS, "ticks");
        map.put(Domain.UNKNOWN, "?");
        domainStrings = Collections.unmodifiableMap(map);
    }
    private static final Map<Domain, String> domainNameStrings;
    static {
        Map<Domain, String> map = new HashMap<>();
        map.put(Domain.MILLISECONDS, "milliseconds");
        map.put(Domain.NOTELENGTH, "note length");
        map.put(Domain.RELATIVE, "relative");
        map.put(Domain.TICKS, "ticks");
        map.put(Domain.UNKNOWN, "unknown");
        domainNameStrings = Collections.unmodifiableMap(map);
    }

    private double value = 0.0;
    private Domain domain = Domain.UNKNOWN;

    /**
     * constructor, generates an instance with initial values
     * @param value
     * @param domain
     */
    private TemporalValue(double value, Domain domain) {
        setDomain(domain);
        setValue(value);
    }

    /**
     * TemporalValue factory
     * @param value
     * @param domain
     * @return
     */
    public static TemporalValue create(double value, Domain domain) {
        return new TemporalValue(value, domain);
    }

    /**
     * return the temporal value
     * @return
     */
    public double getValue() {
        return value;
    }

    /**
     * sets value as new temporal value
     * @param value
     */
    public void setValue(double value) {
        this.value = value;
    }
    /**
     * sets value of temporal as new temporal value
     * @param temporal
     */
    public void setValue(TemporalValue temporal) {
        setValue(temporal.getValue());
        setDomain(temporal.getDomain());
    }
    /**
     * tries to set value from string, if it contains domain string, domain is set as well!
     * @param valueString
     */
    public void setValue(String valueString) {
        fromString(valueString);
    }

    /**
     * return the temporal domain
     * @return
     */
    public Domain getDomain() {
        return domain;
    }

    /**
     * sets domain as new temporal domain, temporal value remains untouched
     * @param domain
     */
    public void setDomain(Domain domain) {
        if(domain == null)
            return;
        this.domain = domain;
    }

    /**
     * tries to set domain from string
     * @param domainString
     */
    public void setDomain(String domainString) {
        setDomain(fromDomainString(domainString));
    }

    /**
     * returns a TemporalValue object that is relative in its value to value.
     * @param value
     * @return
     */
    public TemporalValue getRelativeTo(double value) {
        TemporalValue relative = create(value, Domain.RELATIVE);

        if(getValue() == value) {
            relative.setValue("100");
            return relative;
        }

        double greaterValue = getValue();
        double lesserValue = value;
        if(getValue() < value) {
            greaterValue = value;
            lesserValue = getValue();
        }

        double relativeValue = lesserValue * 100 / greaterValue;
        relative.setValue(relativeValue);

        return relative;
    }
    /**
     * returns a TemporalValue object that is relative regarding its value to temporal's value.
     * @param temporal
     * @return
     */
    public TemporalValue getRelativeTo(TemporalValue temporal) {
        if(!hasSameDomain(temporal))
            return null;

        return getRelativeTo(temporal.getValue());
    }

    /**
     * set value after applying the relativValue to it
     * @param relativValue
     * @return
     */
    public double applyRelative(double relativValue) {
        setValue(getValue() * (relativValue / 100));
        return getValue();
    }
    /**
     * set value after applying the value of relativeTemporal to it
     * @param relativeTemporal
     * @return
     */
    public double applyRelative(TemporalValue relativeTemporal) {
        if(!relativeTemporal.isRelative())
            return getValue();
        return applyRelative(relativeTemporal.getValue());
    }

    /**
     * adds value to this value
     * @param value
     * @return
     */
    public double add(double value) {
        setValue(getValue() + value);
        return getValue();
    }

    /**
     * adds temporal's value to this value
     * @param temporal
     * @return
     */
    public double add(TemporalValue temporal) {
        if(hasSameDomain(temporal))
            return add(temporal.getValue());
        return getValue();
    }

    /**
     * substracts value from this value
     * @param value
     * @return
     */
    public double subtract(double value) {
        setValue(getValue() - value);
        return getValue();
    }

    /**
     * substracts temporal's value from this value
     * @param temporal
     * @return
     */
    public double subtract(TemporalValue temporal) {
        if(hasSameDomain(temporal))
            return subtract(temporal.getValue());
        return getValue();
    }

    /**
     * returns if this value is greater than the given value
     * @param value
     * @return
     */
    public boolean isGreater(double value) {
        return getValue() > value;
    }
    /**
     * returns if this value is greater than temporal's value. If temporal is not in the same Domain the result is false
     * @param temporal
     * @return
     */
    public boolean isGreater(TemporalValue temporal) {
        if(hasSameDomain(temporal))
            return isGreater(temporal.getValue());
        return false;
    }
    /**
     * returns if this value is less than the given value
     * @param value
     * @return
     */
    public boolean isLess(double value) {
        return getValue() < value;
    }
    /**
     * returns if this value is less than temporal's value. If temporal is not in the same Domain the result is false
     * @param temporal
     * @return
     */
    public boolean isLess(TemporalValue temporal) {
        if(hasSameDomain(temporal))
            return isLess(temporal.getValue());
        return false;
    }

    /**
     * returns the TemporalValue with greater value of a and b. If equal, a is returned.
     * @param a
     * @param b
     * @return
     */
    public static TemporalValue getGreater(TemporalValue a, TemporalValue b) {
        if(b.isGreater(a))
            return b;
        return a;
    }
    /**
     * returns the TemporalValue a or b which is less in value. If equal, b is returned.
     * @param a
     * @param b
     * @return
     */
    public static TemporalValue getLess(TemporalValue a, TemporalValue b) {
        if(a.isLess(b))
            return a;
        return b;
    }

    /**
     * returns if this domain is equal to temporal's domain
     * @param temporal
     * @return
     */
    public boolean hasSameDomain(TemporalValue temporal) {
        return getDomain() == temporal.getDomain();
    }

    /**
     * returns if this value is equal to temporsl's value
     * @param temporal
     * @return
     */
    public boolean hasSameValue(TemporalValue temporal) {
        return getValue() == temporal.getValue();
    }

    /**
     * returns if equal, both value and domain
     * @param temporal
     * @return
     */
    public boolean equals(TemporalValue temporal) {
        return hasSameDomain(temporal) && hasSameValue(temporal);
    }

    /**
     * return if it is a relative value
     * @return
     */
    public boolean isRelative() {
        return getDomain() == Domain.RELATIVE;
    }

    /**
     * returns if it is in milliseconds
     * @return
     */
    public boolean isMilliseconds() {
        return getDomain() == Domain.MILLISECONDS;
    }

    /**
     * returns if it is in ticks
     * @return
     */
    public boolean isTicks() {
        return getDomain() == Domain.TICKS;
    }

    /**
     * returns if it is a note length
     * @return
     */
    public boolean isNoteLength() {
        return getDomain() == Domain.NOTELENGTH;
    }

    /**
     * returns if the domain is unknown
     * @return
     */
    public boolean isUnknown() {
        return getDomain() == Domain.UNKNOWN;
    }

    /**
     * stringifies as value + unit
     * @return
     */
    public String toString() {
        return Double.toString(value) + getDomainString();
    }

    /**
     * sets value and domain from string with value + unit
     * @param valueDomain
     */
    public void fromString(String valueDomain) {
        Pattern pattern = Pattern.compile("^(\\d+)(ms|th|%|ticks|\\?)$");
        Matcher m = pattern.matcher(valueDomain);
        if (m.matches()) {
            setValue(Double.parseDouble(m.group(1)));
            setDomain(fromDomainString(m.group(2)));
        }
    }

    /**
     * return this domain unit string
     * @return
     */
    public String getDomainString() {
        return toDomainString(domain);
    }

    /**
     * return the unit string regarding domain
     * @param domain
     * @return
     */
    public static String toDomainString(Domain domain) {
        return domainStrings.get(domain);
    }

    /**
     * returns the Domain regarding the unit in domainString
     * @param domainString
     * @return
     */
    public static Domain fromDomainString(String domainString) {
        for (Map.Entry<Domain, String> entry : domainStrings.entrySet()) {
            if (entry.getValue().equals(domainString)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * returns the name of domain, e.g. for displaying
     * @param domain
     * @return
     */
    public static String toDomainName(Domain domain) {
        return domainNameStrings.get(domain);
    }

    /**
     * returns the Domain regarding the corresponding domainName
     * @param domainName
     * @return
     */
    public static Domain fromDomainName(String domainName) {
        for (Map.Entry<Domain, String> entry : domainNameStrings.entrySet()) {
            if (entry.getValue().equals(domainName)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
