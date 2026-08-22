import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";

export function WeekNavigator({ onPrevious, onNext, previousDisabled, nextDisabled }: {
  onPrevious: () => void;
  onNext: () => void;
  previousDisabled?: boolean;
  nextDisabled?: boolean;
}) {
  return <div className="flex items-center gap-2">
    <Button aria-label="Xem tuần trước" variant="secondary" size="sm" disabled={previousDisabled} onClick={onPrevious}><ChevronLeft size={16}/><span className="hidden sm:inline">Tuần trước</span></Button>
    <Button aria-label="Xem tuần sau" variant="secondary" size="sm" disabled={nextDisabled} onClick={onNext}><span className="hidden sm:inline">Tuần sau</span><ChevronRight size={16}/></Button>
  </div>;
}
