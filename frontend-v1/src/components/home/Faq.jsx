const Faq = () => {
  return (
    <section className="bg-background py-12 px-6">
      <div className="max-w-7xl mx-auto">
        {/* FAQ heading and questions are temporarily hidden; the CTA below remains visible. */}

        <div className="surface-card mt-8 px-8 py-6 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <p className="text-lg font-bold text-foreground">Ready to see your pipeline?</p>
            <p className="text-sm text-muted-foreground mt-1">Start a 14-day trial — no credit card, full feature access.</p>
          </div>
          <div className="flex items-center gap-3 w-full sm:w-auto shrink-0">
            <button className="px-5 py-2 rounded-xl text-sm font-semibold gradient-primary text-primary-foreground cursor-pointer border-none hover:scale-105 hover:opacity-80 transition-all duration-300 ease-in-out">
              Compare plans
            </button>
            <button className="px-5 py-2 rounded-xl text-sm font-semibold bg-surface border border-border text-foreground cursor-pointer hover:scale-105 hover:bg-muted transition-all duration-300 ease-in-out">
              Talk to sales
            </button>
          </div>
        </div>
      </div>
    </section>
  );
};

export default Faq;
