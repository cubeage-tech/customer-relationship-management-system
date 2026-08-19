import { useState } from 'react';

const FAQS = [
  {
    question: 'What is SmartCRM AI?',
    answer:
      'SmartCRM AI is an AI-powered Customer Relationship Management platform that helps companies manage customers, leads, sales, quotations, communication and service requests.',
  },
  {
    question: 'Who can use SmartCRM AI?',
    answer:
      'Sales, marketing, service, finance and management teams can use SmartCRM AI, with access based on their assigned role.',
  },
  {
    question: 'What is a lead in CRM?',
    answer:
      'A lead is a potential customer who may be interested in the company’s products or services.',
  },
  {
    question: 'What is customer management?',
    answer:
      'Customer management allows companies to store and manage customer details, contacts, purchase history, communication and service information in one place.',
  },
  {
    question: 'What is Customer 360?',
    answer:
      'Customer 360 provides a complete view of a customer, including company details, contacts, purchases, quotations, communication and service history.',
  },
  {
    question: 'How does lead management work?',
    answer:
      'Leads can be created, assigned to salespeople, followed up and moved through stages such as New, Contacted, Meeting, Quotation, Negotiation and Converted.',
  },
  {
    question: 'What is the sales pipeline?',
    answer:
      'The sales pipeline shows opportunities at different sales stages and helps teams track deal value, progress and expected closing dates.',
  },
  {
    question: 'Can users create quotations?',
    answer:
      'Yes. Authorized users can create, manage and track customer quotations, including pricing, discounts, approval and customer status.',
  },
  {
    question: 'Can managers approve quotations?',
    answer:
      'Yes. Managers can review and approve quotations based on company rules, while authorized finance users can approve discounts and pricing exceptions.',
  },
  {
    question: 'Does SmartCRM AI support service requests?',
    answer:
      'Yes. Service teams can create and manage customer complaints, service tickets, priorities, technician assignments, SLA tracking and feedback.',
  },
  {
    question: 'How is user access controlled?',
    answer:
      'Each user has a specific CRM role. The role determines which modules and records the user can view, create, update or manage.',
  },
  {
    question: 'Does the CRM support multiple organisations?',
    answer:
      'Yes. Each organisation can operate as a separate tenant with its own users, customers, leads, sales data and administrator.',
  },
  {
    question: 'What is AI Lead Scoring?',
    answer:
      'AI Lead Scoring analyzes lead information, interactions and buying behavior to estimate the probability of converting a lead into a customer.',
  },
  {
    question: 'Can AI predict sales?',
    answer:
      'Yes. AI can analyze sales opportunities and historical data to forecast future revenue, deal probability and sales performance.',
  },
  {
    question: 'Can AI recommend products to customers?',
    answer:
      'Yes. AI can analyze customer purchase history and behavior to recommend relevant products or services.',
  },
  {
    question: 'Does the CRM provide an AI assistant?',
    answer:
      'Yes. Employees can ask questions about CRM data and receive useful information and recommendations through the AI assistant.',
  },
  {
    question: 'Can AI generate emails?',
    answer:
      'Yes. The AI assistant can generate professional sales emails, follow-up messages, marketing content and customer communication.',
  },
  {
    question: 'Does the CRM provide analytics?',
    answer:
      'Yes. The analytics dashboard provides information about leads, conversions, revenue, sales performance, customers and business growth.',
  },
  {
    question: 'Can CRM users track customer communication?',
    answer:
      'Yes. Users can maintain records of emails, calls, meetings, messages and other customer interactions.',
  },
  {
    question: 'Can CRM send follow-up reminders?',
    answer:
      'Yes. Users can schedule follow-ups and receive reminders so that important customer interactions are not missed.',
  },
  {
    question: 'Is SmartCRM AI suitable for different industries?',
    answer:
      'Yes. SmartCRM AI can be customized for industries such as manufacturing, engineering, real estate, construction, automobile, software, healthcare and trading.',
  },
];

const Faq = () => {
  const [openIndex, setOpenIndex] = useState(null);

  return (
    <section className="max-w-full py-12">
      <h2 className="text-2xl text-center font-bold text-foreground mb-6">Frequently asked questions</h2>

      <div className="surface-card divide-y divide-border overflow-hidden">
        {FAQS.map((faq, i) => (
          <div key={faq.question}>
            <button
              onClick={() => setOpenIndex(openIndex === i ? null : i)}
              className="w-full flex justify-between items-center px-5 py-4 text-left bg-transparent border-none cursor-pointer"
            >
              <span className="font-semibold text-foreground text-sm">{faq.question}</span>
              <span className="text-muted-foreground text-lg ml-4">{openIndex === i ? '−' : '+'}</span>
            </button>
            {openIndex === i && (
              <p className="text-sm text-muted-foreground px-5 pb-4">{faq.answer}</p>
            )}
          </div>
        ))}
      </div>

      <div className="surface-card mt-8 px-8 py-6 flex items-center justify-between gap-6">
        <div>
          <p className="text-lg font-bold text-foreground">Ready to see your pipeline?</p>
          <p className="text-sm text-muted-foreground mt-1">Start a 14-day trial — no credit card, full feature access.</p>
        </div>
        <div className="flex items-center gap-3 shrink-0">
          <button className="px-5 py-2 rounded-xl text-sm font-semibold gradient-primary text-primary-foreground cursor-pointer border-none hover:scale-105 hover:opacity-80 transition-all duration-300 ease-in-out">
            Compare plans
          </button>
          <button className="px-5 py-2 rounded-xl text-sm font-semibold bg-surface border border-border text-foreground cursor-pointer hover:scale-105 hover:bg-muted transition-all duration-300 ease-in-out">
            Talk to sales
          </button>
        </div>
      </div>
    </section>
  );
};

export default Faq;
