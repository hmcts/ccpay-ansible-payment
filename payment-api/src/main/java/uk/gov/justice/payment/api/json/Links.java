
package uk.gov.justice.payment.api.json;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
    "self",
    "cancel",
    "events",
    "refunds"
})
public class Links {

    @JsonProperty("self")
    private Self self;
    @JsonProperty("cancel")
    private Object cancel;
    @JsonProperty("events")
    private Events events;
    @JsonProperty("refunds")
    private Refunds refunds;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * 
     * @return
     *     The self
     */
    @JsonProperty("self")
    public Self getSelf() {
        return self;
    }

    /**
     * 
     * @param self
     *     The self
     */
    @JsonProperty("self")
    public void setSelf(Self self) {
        this.self = self;
    }

    /**
     * 
     * @return
     *     The cancel
     */
    @JsonProperty("cancel")
    public Object getCancel() {
        return cancel;
    }

    /**
     * 
     * @param cancel
     *     The cancel
     */
    @JsonProperty("cancel")
    public void setCancel(Object cancel) {
        this.cancel = cancel;
    }

    /**
     * 
     * @return
     *     The events
     */
    @JsonProperty("events")
    public Events getEvents() {
        return events;
    }

    /**
     * 
     * @param events
     *     The events
     */
    @JsonProperty("events")
    public void setEvents(Events events) {
        this.events = events;
    }

    /**
     * 
     * @return
     *     The refunds
     */
    @JsonProperty("refunds")
    public Refunds getRefunds() {
        return refunds;
    }

    /**
     * 
     * @param refunds
     *     The refunds
     */
    @JsonProperty("refunds")
    public void setRefunds(Refunds refunds) {
        this.refunds = refunds;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
