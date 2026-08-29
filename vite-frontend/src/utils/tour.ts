import { driver } from "driver.js";
import "driver.js/dist/driver.css";

// ha-min: 气泡引导仅在 DOM 元素存在时执行；目标元素由页面自身渲染，找不到时整步跳过
export interface TourStep {
  element?: string;
  popover: { title: string; description: string };
}

const seenKey = (key: string) => `${key}_seen`;

export const hasSeenTour = (key: string) => localStorage.getItem(seenKey(key)) === "1";

export const markTourSeen = (key: string) => localStorage.setItem(seenKey(key), "1");

export const runTour = (key: string, steps: TourStep[], opts?: { force?: boolean }) => {
  const visibleSteps = steps.filter((step) => !step.element || document.querySelector(step.element));
  if (visibleSteps.length === 0) return;
  if (!opts?.force && hasSeenTour(key)) return;
  markTourSeen(key);
  const driverObj = driver({
    showProgress: true,
    allowClose: true,
    nextBtnText: "下一步",
    prevBtnText: "上一步",
    doneBtnText: "完成",
    progressText: "{{current}} / {{total}}",
    steps: visibleSteps,
  });
  driverObj.drive();
};

export const startTourManually = (key: string, steps: TourStep[]) => runTour(key, steps, { force: true });
