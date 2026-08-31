const FAQ = () => {
  const faqs = [
    {
      question: "Can I change plans later?",
      answer: "Yes — upgrade or downgrade anytime, billing is prorated to the day."
    },
    {
      question: "What are AI credits?",
      answer: "Credits power scoring, summaries and drafting. Unused credits roll over one month."
    },
    {
      question: "Is there a free trial?",
      answer: "Every plan includes a 14-day trial with full feature access and no credit card."
    }
  ];

  return (
    <div className="w-full max-w-7xl mx-auto px-6 py-8 mb-16">
      <h2 className="text-2xl font-bold text-slate-900 mb-6">Pricing FAQ</h2>
      
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {faqs.map((faq, idx) => (
          <div key={idx} className="bg-white rounded-2xl border border-slate-200 p-6 shadow-[0_2px_10px_-4px_rgba(0,0,0,0.05)]">
            <h3 className="font-semibold text-slate-900 mb-3">{faq.question}</h3>
            <p className="text-sm text-slate-500 leading-relaxed">{faq.answer}</p>
          </div>
        ))}
      </div>
    </div>
  );
};

export default FAQ;