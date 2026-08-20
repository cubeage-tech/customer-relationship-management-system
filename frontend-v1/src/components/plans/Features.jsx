import { Check, Minus } from 'lucide-react';

const Features = () => {
  const features = [
    {
      name: 'Drag-and-drop pipeline',
      starter: true,
      growth: true,
      scale: true,
      enterprise: true
    },
    {
      name: 'AI lead scoring',
      starter: false,
      growth: true,
      scale: true,
      enterprise: true
    },
    {
      name: 'Marketing ROI dashboards',
      starter: false,
      growth: true,
      scale: true,
      enterprise: true
    },
    {
      name: 'Workflow automation',
      starter: false,
      growth: false,
      scale: true,
      enterprise: true
    },
    {
      name: 'Revenue forecasting',
      starter: false,
      growth: false,
      scale: true,
      enterprise: true
    },
    {
      name: 'SSO & audit logs',
      starter: false,
      growth: false,
      scale: false,
      enterprise: true
    }
  ];

  const renderIcon = (included) => {
    return included ? (
      <Check size={18} className="text-emerald-500 mx-auto" />
    ) : (
      <Minus size={18} className="text-slate-300 mx-auto" />
    );
  };

  return (
    <div className="w-full max-w-6xl mx-auto px-4 py-8">
      <h2 className="text-2xl font-bold text-slate-900 mb-6">Compare features</h2>
      
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-[0_2px_10px_-4px_rgba(0,0,0,0.05)]">
        <div className="grid grid-cols-5 p-6 border-b border-slate-100 text-[11px] font-bold text-slate-500 tracking-wider">
          <div className="col-span-1">FEATURE</div>
          <div className="text-center">STARTER</div>
          <div className="text-center">GROWTH</div>
          <div className="text-center">SCALE</div>
          <div className="text-center">ENTERPRISE</div>
        </div>
        
        <div className="flex flex-col">
          {features.map((feature, idx) => (
            <div 
              key={idx} 
              className={`grid grid-cols-5 px-6 py-5 items-center text-sm ${
                idx !== features.length - 1 ? 'border-b border-slate-100' : ''
              }`}
            >
              <div className="col-span-1 font-medium text-slate-700">{feature.name}</div>
              <div>{renderIcon(feature.starter)}</div>
              <div>{renderIcon(feature.growth)}</div>
              <div>{renderIcon(feature.scale)}</div>
              <div>{renderIcon(feature.enterprise)}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default Features;