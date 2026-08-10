const FAQS = [
  {
    question: 'Who can use the CRM?',
    answer:
      'Sales, marketing, service and finance teams, each with their own role and level of access.',
  },
  {
    question: 'How is access controlled?',
    answer:
      'Every user holds one CRM role. The role decides which modules they see and whether they work with their own, their team’s or all records.',
  },
  {
    question: 'Can managers approve quotations?',
    answer:
      'Yes. Sales managers approve their team’s quotations, and finance approvers sign off on discounts.',
  },
  {
    question: 'Does it support multiple organisations?',
    answer:
      'Yes. Each organisation is a separate tenant, managed by its own tenant administrator.',
  },
];

const Faq = () => {
  return (
    <section className="max-w-3xl mx-auto px-6 py-12">
      <h2 className="text-2xl font-bold text-slate-900 mb-6">
        Frequently asked questions
      </h2>

      <div className="space-y-4">
        {FAQS.map((faq) => (
          <article
            key={faq.question}
            className="bg-white border border-slate-200 rounded-lg p-5"
          >
            <h3 className="font-semibold text-slate-900">{faq.question}</h3>
            <p className="text-sm text-slate-600 mt-1">{faq.answer}</p>
          </article>
        ))}
      </div>
    </section>
  );
};

export default Faq;
