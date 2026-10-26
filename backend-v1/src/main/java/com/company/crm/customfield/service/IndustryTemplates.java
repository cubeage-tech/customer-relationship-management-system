package com.company.crm.customfield.service;

import com.company.crm.customfield.dto.CustomFieldDtos.Template;
import com.company.crm.customfield.dto.CustomFieldDtos.TemplateField;

import java.util.List;
import java.util.Optional;

/**
 * ARC-9: built-in starting points per default industry (SRS 6.1 codes). Applying one adds its
 * fields for that industry only; admins can then rename, extend or switch them off.
 */
public final class IndustryTemplates {

    private IndustryTemplates() {
    }

    private static TemplateField f(String entity, String key, String label, String type, String... options) {
        return new TemplateField(entity, key, label, type, options.length == 0 ? null : List.of(options), false);
    }

    public static final List<Template> ALL = List.of(
            new Template("manufacturing", "Manufacturing", List.of(
                    f("customer", "plant_capacity", "Plant capacity (units per month)", "number"),
                    f("customer", "quality_certifications", "Quality certifications", "text"),
                    f("opportunity", "order_volume", "Order volume (units)", "number"),
                    f("opportunity", "delivery_schedule", "Delivery schedule", "select", "One-time", "Monthly", "Quarterly"))),
            new Template("engineering", "Engineering", List.of(
                    f("customer", "project_type", "Project type", "select", "Design", "Consulting", "Turnkey"),
                    f("opportunity", "project_duration_months", "Project duration (months)", "number"))),
            new Template("real_estate", "Real Estate", List.of(
                    f("lead", "budget_range", "Budget range", "select", "Under 50 lakh", "50 lakh to 1 crore", "1 to 2 crore", "Over 2 crore"),
                    f("lead", "property_type", "Property type", "select", "Apartment", "Villa", "Plot", "Commercial"),
                    f("lead", "preferred_location", "Preferred location", "text"),
                    f("opportunity", "possession_date", "Possession date", "date"))),
            new Template("construction", "Construction", List.of(
                    f("customer", "contractor_licence", "Contractor licence number", "text"),
                    f("opportunity", "site_location", "Site location", "text"),
                    f("opportunity", "project_area_sqft", "Project area (sq ft)", "number"))),
            new Template("automobile", "Automobile", List.of(
                    f("lead", "vehicle_model", "Vehicle model of interest", "text"),
                    f("lead", "test_drive_done", "Test drive done", "checkbox"),
                    f("opportunity", "trade_in", "Trade-in vehicle", "checkbox"),
                    f("opportunity", "financing_required", "Financing required", "checkbox"))),
            new Template("industrial_equipment", "Industrial Equipment", List.of(
                    f("customer", "installed_base", "Installed equipment", "text"),
                    f("opportunity", "amc_required", "Annual maintenance contract", "checkbox"),
                    f("opportunity", "warranty_years", "Warranty (years)", "number"))),
            new Template("software", "Software", List.of(
                    f("customer", "current_systems", "Current systems", "text"),
                    f("customer", "user_count", "Number of users", "number"),
                    f("opportunity", "licence_model", "Licence model", "select", "Subscription", "Perpetual", "Usage-based"),
                    f("opportunity", "contract_term_months", "Contract term (months)", "number"))),
            new Template("trading", "Trading", List.of(
                    f("customer", "gst_number", "GST number", "text"),
                    f("customer", "credit_limit", "Credit limit", "number"),
                    f("customer", "payment_terms", "Payment terms", "select", "Advance", "Net 15", "Net 30", "Net 60"))),
            new Template("services", "Services", List.of(
                    f("customer", "contract_type", "Contract type", "select", "Retainer", "Project", "Hourly"),
                    f("opportunity", "billing_model", "Billing model", "select", "Fixed price", "Time and materials"))),
            new Template("healthcare", "Healthcare", List.of(
                    f("customer", "facility_type", "Facility type", "select", "Hospital", "Clinic", "Diagnostic lab", "Pharmacy"),
                    f("customer", "bed_count", "Number of beds", "number"),
                    f("lead", "accreditation", "Accreditation", "text"))),
            new Template("other", "Other", List.of(
                    f("customer", "preferred_contact_time", "Preferred contact time", "text"))));

    public static Optional<Template> forIndustry(String industry) {
        return ALL.stream().filter(t -> t.industry().equals(industry)).findFirst();
    }
}
