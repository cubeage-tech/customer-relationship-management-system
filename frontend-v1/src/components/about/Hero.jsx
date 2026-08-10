import { APP_NAME } from '../../core/constants/app.constant';

const Hero = () => {
  return (
    <section className="max-w-3xl mx-auto px-6 py-16">
      <h1 className="text-3xl font-bold text-slate-900">About us</h1>
      <p className="text-slate-600 mt-4">
        {APP_NAME} brings your customer records, sales pipeline, marketing
        campaigns and service tickets together, so every team works from the same
        view of the customer.
      </p>
      <p className="text-slate-600 mt-4">
        Access is organised around clear roles — from sales executives working
        their own leads, to managers approving quotations, to executives tracking
        performance across the whole organisation.
      </p>
    </section>
  );
};

export default Hero;
