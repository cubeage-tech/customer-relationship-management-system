import { useState } from "react";
import { Clock3, Mail, MapPin, Phone, Sparkles } from "lucide-react";

const enquiryTypes = [
  "Book a demo",
  "Pricing & plans",
  "Technical support",
  "Partnerships",
];

const contactDetails = [
  { label: "EMAIL", value: "hello@smartcrm.ai", icon: Mail },
  { label: "PHONE", value: "+1 (415) 555-0148", icon: Phone },
  { label: "HQ", value: "440 Market St, San Francisco", icon: MapPin },
  { label: "RESPONSE TIME", value: "Within 1 business day", icon: Clock3 },
];

const Contact = () => {
  const [selectedType, setSelectedType] = useState(enquiryTypes[0]);
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = (event) => {
    event.preventDefault();
    setSubmitted(true);
  };

  return (
    <section className="page-canvas min-h-[calc(100vh-73px)] px-6 py-16 sm:py-20">
      <div className="mx-auto max-w-5xl">
        <div className="mb-9">
          <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/5 px-3 py-1 text-[10px] font-bold tracking-[0.16em] text-primary">
            <Sparkles size={12} strokeWidth={2.5} />
            CONTACT US
          </div>
          <h1 className="max-w-3xl text-4xl font-bold tracking-tight text-foreground sm:text-[40px]">
            Let&apos;s talk about your revenue stack
          </h1>
          <p className="mt-3 text-sm text-muted-foreground">
            Tell us a little about your team and we&apos;ll tailor a walkthrough around your pipeline.
          </p>
        </div>

        <div className="grid gap-6 lg:grid-cols-[1.35fr_0.85fr] lg:items-start">
          <form onSubmit={handleSubmit} className="glass-panel rounded-3xl p-6 sm:p-7">
            <div className="mb-6 flex flex-wrap gap-2">
              {enquiryTypes.map((type) => (
                <button
                  key={type}
                  type="button"
                  onClick={() => setSelectedType(type)}
                  className={`rounded-full border px-3 py-1.5 text-xs transition-colors ${
                    selectedType === type
                      ? "border-primary/30 bg-primary/10 font-medium text-primary"
                      : "border-border bg-surface/70 text-muted-foreground hover:border-primary/20 hover:text-primary"
                  }`}
                >
                  {type}
                </button>
              ))}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <label className="text-xs font-semibold text-foreground">
                Full name
                <input required name="name" placeholder="John Doe" className="mt-2 w-full rounded-2xl border border-input bg-surface-muted px-3 py-3 text-sm font-normal text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15" />
              </label>
              <label className="text-xs font-semibold text-foreground">
                Work email
                <input required type="email" name="email" placeholder="john@company.com" className="mt-2 w-full rounded-2xl border border-input bg-surface-muted px-3 py-3 text-sm font-normal text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15" />
              </label>
              <label className="text-xs font-semibold text-foreground">
                Company
                <input required name="company" placeholder="Acme Inc." className="mt-2 w-full rounded-2xl border border-input bg-surface-muted px-3 py-3 text-sm font-normal text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15" />
              </label>
              <label className="text-xs font-semibold text-foreground">
                Team size
                <select name="teamSize" defaultValue="11-50" className="mt-2 w-full rounded-2xl border border-input bg-surface-muted px-3 py-3 text-sm font-normal text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15">
                  <option>1-10</option>
                  <option>11-50</option>
                  <option>51-200</option>
                  <option>201+</option>
                </select>
              </label>
            </div>

            <label className="mt-4 block text-xs font-semibold text-foreground">
              How can we help?
              <textarea required name="message" rows="4" placeholder="We're a 30-person sales team moving off spreadsheets..." className="mt-2 w-full resize-none rounded-2xl border border-input bg-surface-muted px-3 py-3 text-sm font-normal text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15" />
            </label>

            <div className="mt-6 flex flex-wrap items-center gap-4">
              <button type="submit" className="gradient-primary rounded-xl px-5 py-3 text-xs font-semibold text-primary-foreground shadow-glow transition hover:opacity-90">
                {submitted ? "Message sent" : "Send message"}
              </button>
              <span className="text-xs text-muted-foreground">No spam, ever.</span>
            </div>
          </form>

          <div className="space-y-4">
            <div className="surface-card p-6">
              <h2 className="text-sm font-bold text-foreground">Reach us directly</h2>
              <div className="mt-5 space-y-4">
                {contactDetails.map(({ label, value, icon: Icon }) => (
                  <div key={label} className="flex items-center gap-3">
                    <div className="gradient-primary flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-primary-foreground shadow-glow">
                      <Icon size={15} />
                    </div>
                    <div>
                      <p className="text-[10px] font-medium tracking-wide text-muted-foreground">{label}</p>
                      <p className="text-xs font-medium text-foreground">{value}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="glass-panel rounded-3xl p-6">
              <h2 className="text-sm font-bold text-foreground">Enterprise enquiries</h2>
              <p className="mt-2 text-xs leading-relaxed text-muted-foreground">
                Need SSO, data residency or a security review? Our enterprise team responds within 4 hours on business days.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default Contact;
